import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;


public class USB_Thread extends Thread {

    private String adbExe;
    private boolean connection;
    private String musicDirectoryPath;
    private String convertMusicTo;
    private boolean useiTunesDataLibraryFile;
    private RandomAccessFile readituneslibrary;
    private String iTunesDataLibraryFile;
    private boolean listen=true;
    
    public USB_Thread(String adbExe, String musicDirectoryPath, String convertMusicTo, boolean useiTunesDataLibraryFile, RandomAccessFile readituneslibrary, String iTunesDataLibraryFile) {
        this.adbExe=adbExe;
        this.musicDirectoryPath = musicDirectoryPath;
        this.convertMusicTo = convertMusicTo;
        this.useiTunesDataLibraryFile = useiTunesDataLibraryFile;
        this.readituneslibrary = readituneslibrary;
        this.iTunesDataLibraryFile = iTunesDataLibraryFile;
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
                
                //get list of songs the phone has
                //first copy file from phone to local
                Runtime runtime = Runtime.getRuntime();
                Process p;
                try {
                    p = runtime.exec(adbExe+" pull /extSdCard/SongSync/SongSync_Song_List.txt /");
                    p.waitFor();
                    //read this into memory
                    BufferedReader in = new BufferedReader(new FileReader("SongSync_Song_List.txt"));
                    ArrayList<String> master_song_list=new ArrayList<String>();
                    while(in.ready()){
                        master_song_list.add(in.readLine());
                    }
                    
                    //generate the list of all songs
                    ArrayList<String> songs=new ArrayList<String>();
                    Desktop_Server.generateList(songs, musicDirectoryPath);
                    
                    //compare
                    FileWriter out=new FileWriter("SongSync_Song_List.txt.new");
                    for(String recieve:songs){
                        //i dont know why android uses a / for a file delimiter and windows uses a \.
                        recieve=recieve.replaceAll("\\\\", "/");
                        
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
                    in.close();
                    out.close();
                    new File("SongSync_Song_List.txt.new").renameTo(new File("SongSync_Song_List.txt"));
                    out=new FileWriter("SongSync_Song_List.txt");
                    
                    //out now has the the same as in, missing songs we remove. do those now
                    for(String rmSong:master_song_list){
                        p=runtime.exec(adbExe+" shell rm "+rmSong);
                        p.waitFor();
                    }
                    //push the updated out file, which matches the music to not include the files we just removed
                    p=runtime.exec(adbExe+" shell rm /extSdCard/SongSync/SongSync_Song_List.txt");
                    p.waitFor();
                    p=runtime.exec(adbExe+" push SongSync_Song_List.txt /extSdCard/SongSync");
                    p.waitFor();
                    
                    //for all the new songs
                    for(String newSong:listOfSongsToAdd){
                        //convert the song
                        String songpath=musicDirectoryPath+newSong;
                        System.out.println("got request for "+newSong);
                        
                        //find the songs filetype, and convert it if it needs to be converted
                        String filetype=songpath.substring(songpath.lastIndexOf("."));
                        //if we're using iTunes we always need to remux to add iTunes metadata and art
                        if((!convertMusicTo.equals("") && !filetype.equals(convertMusicTo)) || useiTunesDataLibraryFile){
                                String metadata="";
                                if(useiTunesDataLibraryFile){
                                    metadata=iTunesInterface.scanForitunesMetadata(newSong,readituneslibrary,iTunesDataLibraryFile);
                                }
                                try {
                                    Desktop_Server.conversion(songpath, metadata);
                                } catch (IOException e) {
                                    System.err.println(e.getMessage()+". Aborting sync.");
                                    out.close();
                                    in.close();
                                    System.exit(0);
                                }
                                //change the file to point to the converted song
                                songpath="tempout"+convertMusicTo;
                        }

                        //write song to phone
                        p=runtime.exec(adbExe+" push tempout"+convertMusicTo+" /extSdCard/SongSync/Music/");
                        p.waitFor();

                        //append the new written song to the txt list, and push the updated list to the phone. This is slow, but allows resume from interrupts.

                    }
                    
                } catch (IOException | InterruptedException e) {
                    System.err.println("Unrecoverable usb reading error.");
                    e.printStackTrace();
                }
            }
        }
        System.out.println("USB listener exiting.");
    }

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
    
    public void try_force_connection(){
        connection=true;
    }
}