package threads;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;

import main.Desktop_Server;
import musicPlayerInterface.iTunesInterface;

import org.javatuples.Pair;


public class USB_Thread extends Parent_Thread {

    private String adbExe;
    private boolean connection;
    private boolean listen=true;
    
    public USB_Thread(String adbExe, String musicDirectoryPath, String convertMusicTo,
            boolean useiTunesDataLibraryFile,
            RandomAccessFile readituneslibrary, String iTunesDataLibraryFile,
            String ffmpegEXElocation, String ffmpegCommand,
            HashMap<String, String> codecs) {
        
        super(musicDirectoryPath, convertMusicTo, useiTunesDataLibraryFile, readituneslibrary, iTunesDataLibraryFile, ffmpegEXElocation, ffmpegCommand, codecs);
        
        this.adbExe=adbExe;
        //start the adb listening daemon
        if(!adbExe.isEmpty()){
            Runtime runtime = Runtime.getRuntime();
            try {
                Process p=runtime.exec(adbExe+" start-server");
                p.waitFor();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                System.err.println("Error starting usb listener. Is your adb.exe location correct?");
            }
        }
    }
    
    /**
     * Whenever it detects a phone plugged in, auto sync. Do not sync anymore while phone plugged in unless user triggers.
     */
    @Override
    public void run() {
        //loop and listen for connection
        while(listen){
            //check to see if a phone is plugged in
            boolean newConnection=false;
            try{
                //execute command to get list of devices
                Runtime runtime = Runtime.getRuntime();
                Process p=runtime.exec(adbExe+" devices");
                BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream()));
                br.readLine();//ignore first line
                //read if a device connected
                if(!br.readLine().isEmpty()){
                    newConnection=true;
                }
                //if a device is not connected, reset connection state
                else{
                    connection=false;
                }
            }catch(IOException e){
                e.printStackTrace();
            }
            
            //sync
            if(newConnection && !connection){
                connection=true;//phone is connected, dont resync
                System.out.println("Sync Connection via USB");
                
                ArrayList<String> listOfSongsToAdd=new ArrayList<String>();
                ArrayList<String> master_song_list=new ArrayList<String>();

                //get list of songs the phone has
                Runtime runtime = Runtime.getRuntime();
                try {
                    generateSongListFromMasterList(runtime, listOfSongsToAdd, master_song_list);
                    BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream("SongSync_Song_List.txt"), "utf-8"));
                    
                    //out now has the the same as in, missing songs we remove. remove those now
                    for(String rmSong:master_song_list){
                        runtime.exec(adbExe+" shell rm \"/extSdCard/SongSync/Music"+rmSong+"\"").waitFor();
                    }
                    //push the updated out file, which matches the music to not include the files we just removed. This overwrites existing file
                    runtime.exec(adbExe+" push SongSync_Song_List.txt /extSdCard/SongSync/SongSync_Song_List.txt").waitFor();
                    
                    //for all the new songs
                    for(int i=0;i<listOfSongsToAdd.size();i++){
                        String newSong=listOfSongsToAdd.get(i);
                        try{
                            convertSong(newSong);
                            
                            System.out.print(" Sending song. %"+((double)i/(double)listOfSongsToAdd.size())*100);
                            //write song to phone
                            runtime.exec(adbExe+" push tempout"+convertMusicTo+" \"/extSdCard/SongSync/Music"+newSong+"\"").waitFor();

                            //append the new written song to the txt list, and push the updated list to the phone. This is slow, but allows resume from interrupts.
                            out.write(newSong+"\n");
                            out.flush();
                            runtime.exec(adbExe+" push SongSync_Song_List.txt /extSdCard/SongSync/SongSync_Song_List.txt").waitFor();//TODO if this is interrupted, the file is lost.
                            
                        }catch(IOException | InterruptedException e){
                            e.printStackTrace();
                            System.out.println("Conversion error for "+newSong);
                        }
                    }
                    
                    if(useiTunesDataLibraryFile){
                        ArrayList<Pair<String, ArrayList<String>>> playlists=iTunesInterface.generateM3UPlaylists(readituneslibrary);
                        for(Pair<String,ArrayList<String>> playlist:playlists){
                            //write this playlist to a local file
                            File playlistfile=new File(playlist.getValue0()+".m3u");
                            BufferedWriter plout=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(playlistfile), "utf-8"));
                            //write all songs in playlist
                            for(String song:playlist.getValue1()){
                                plout.write(song+"\n");
                            }
                            plout.close();
                            //write this file to the phone
                            runtime.exec(adbExe+" push "+playlist.getValue0()+".m3u \"/extSdCard/SongSync/PlayLists/"+playlist.getValue0()+".m3u\"").waitFor();
                        }
                    }
                    
                    out.close();
                } catch (IOException | InterruptedException e) {
                    System.err.println("Unrecoverable usb reading or execution error.");
                    e.printStackTrace();
                    clean_tmp();
                }
            }
            clean_tmp();
        }
        clean_tmp();
        System.out.println("USB listener exiting.");
    }

    /**
     * At end, the file SongSync_Song_List.txt will be in local dir, with all the songs already on phone, minus ones to remove
     * listOfSongsToAdd will have all songs to be sent to phone
     * master_song_list will have all songs to be removed from phone
     * @param runtime
     * @param listOfSongsToAdd
     * @param master_song_list 
     * @throws IOException
     * @throws InterruptedException
     */
    private void generateSongListFromMasterList(Runtime runtime, ArrayList<String> listOfSongsToAdd, ArrayList<String> master_song_list) throws IOException, InterruptedException {
        //first copy file from phone to local
        runtime.exec(adbExe+" pull /extSdCard/SongSync/SongSync_Song_List.txt SongSync_Song_List.txt").waitFor();
        //create an empty file if SongSync_Song_List.txt does not exist
        File ssl=new File("SongSync_Song_List.txt");
        ssl.createNewFile();
        
        //read this into memory
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(ssl), "utf-8"));
        while(in.ready()){
            master_song_list.add(in.readLine());
        }
        in.close();
        
        //generate the list of all songs
        ArrayList<String> songs=new ArrayList<String>();
        generateList(songs, musicDirectoryPath);
        
        //compare
        PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream("SongSync_Song_List.txt.new"), "utf-8"));
        for(String recieve:songs){            
            //for each song title, check if we already have it on the phone
            //and we are doing a normal sync
            if(master_song_list.contains(recieve) && Desktop_Server.sync_type=="N"){
                //if so, we remove it from the list. At the end, the songs remaining in the list no longer exist on the pc and will be removed from the phone
                master_song_list.remove(recieve);
                //also write this song, which we have, to the master list
                out.write(recieve+"\n");
            }
            else{
                //if it isnt in the previous master list, or we are doing a full resync, we need to get it
                listOfSongsToAdd.add(recieve);
            }
        }
        out.close();
        ssl.delete();
        new File("SongSync_Song_List.txt.new").renameTo(new File("SongSync_Song_List.txt"));
    }

    /**
     * End thread
     */
    public void stop_connection() {
        Runtime runtime = Runtime.getRuntime();
        try {
            runtime.exec(adbExe+" kill-server");
        } catch (IOException e) {
            System.err.println("unable to kill usb listener.");
            e.printStackTrace();
        }
        listen=false;
    }
    
    /**
     * Clean tmp files created. This stops accidental drifts from last run.
     */
    private void clean_tmp(){
        new File("SongSync_Song_List.txt").delete();
        new File("tempout"+convertMusicTo).delete();
        new File("tempalbumart.jpg").delete();
        try {
            Runtime.getRuntime().exec("del *.m3u");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * If phone is plugged in, sync
     */
    public void try_force_connection(){
        connection=true;
    }
    
}