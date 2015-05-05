package threads;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;

import main.Desktop_Server;
import musicPlayerInterface.iTunesInterface;

import org.javatuples.Pair;


public class Listener_Thread extends Parent_Thread {
    private ServerSocket androidConnection;
    private boolean listen=true;
    
    public Listener_Thread(String musicDirectoryPath, String convertMusicTo,
            boolean useiTunesDataLibraryFile,
            RandomAccessFile readituneslibrary, String iTunesDataLibraryFile,
            String ffmpegEXElocation, String ffmpegCommand,
            HashMap<String, String> codecs) {
        super(musicDirectoryPath, convertMusicTo, useiTunesDataLibraryFile, readituneslibrary, iTunesDataLibraryFile, ffmpegEXElocation, ffmpegCommand, codecs);
    }
    
    public void stop_connection(){
        try {
            androidConnection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        listen=false;
    }

    @Override
    public void run() {
        try{
            //open server socket
            androidConnection=new ServerSocket(9091);
            System.out.println("Listening on port "+androidConnection.getLocalPort()+" at host "+androidConnection.getInetAddress().getHostName());
            
            //loop and listen for connection
            while(listen){
                try{
                    Socket phone = androidConnection.accept();
                    System.out.println("Sync Connection Recived.");
                    
                    PrintWriter out=new PrintWriter(new OutputStreamWriter(phone.getOutputStream(), "utf-8"), true);//writer for song names/length
                    BufferedReader in=new BufferedReader(new InputStreamReader(phone.getInputStream(), "utf-8"));//listener for phone requests/info
                    BufferedOutputStream pout=new BufferedOutputStream(phone.getOutputStream());//writer for the song bytes
                    
                    //recieve all the songs the phone wants to delete
                    String song_to_delete=in.readLine();
                    while(song_to_delete!=null && !song_to_delete.equals("END OF SONG DELETIONS")){
                        removeSong(song_to_delete);
                        song_to_delete=in.readLine();
                    }
    
                    //write out sync type(normal "N", full resync "R")
                    out.println(Desktop_Server.sync_type);
                    Desktop_Server.sync_type="N";//change back sync type to normal, now that we've done a full resync
                    
                    //generate the list of all songs
                    ArrayList<String> songs=new ArrayList<String>();
                    generateList(songs, musicDirectoryPath);
                    //write the filetype of the songs
                    out.println(convertMusicTo);
                    //write out all songs
                    for(String song:songs){
                        out.println(song);
                    }
                    //tell phone done writing song list
                    out.println("ENDOFLIST");
                    songs=null;//gc this, huge list of strings that we no longer need
                    
                    //recieve the request list from the phone and send over each song per request
                    String request=in.readLine();
                    while(request!=null && !request.equals("END OF SONG DOWNLOADS")){
                        try{
                            String songpath=convertSong(request);
                            sendSong(songpath, out, pout, in);
                        }catch(IOException | InterruptedException e){
                            e.printStackTrace();
                            System.out.println("Converion failure for "+request);
                        }
                        request=in.readLine();
                    }
                    
                    //send over the playlists
                    /* Sending Protocol:
                     * "NEW LIST" (except for 1st sent list)
                     * playlist name
                     * all songs
                     * repeat
                     * "NO MORE PLAYLISTS"
                     */
                    if(useiTunesDataLibraryFile){
                        ArrayList<Pair<String, ArrayList<String>>> playlists=iTunesInterface.generateM3UPlaylists(readituneslibrary);
                        for(Pair<String,ArrayList<String>> playlist:playlists){
                            out.println(playlist.getValue0());//write playlist name
                            //write all songs in playlist
                            for(String song:playlist.getValue1()){
                                out.println(song);
                            }
                            out.println("NEW LIST");
                        }
                    }
                    out.println("NO MORE PLAYLISTS");
                    
                    out.close();
                    in.close();
                    pout.close();       
                    phone.close();
                    System.out.println("Sync finished");
                }catch(Exception e){
                    System.err.println("Unrecoverable network/file io error.");
                    e.printStackTrace();
                }
            }
            
        }catch(SocketException e){
            System.out.println("Wifi Thread closed.");
            e.printStackTrace();
        }catch(Exception e){
            System.err.println("Unrecoverable network/file io error.");
            e.printStackTrace();
        }
    }

    /**
     * Given songpath, send the song to the phone
     * @param songpath
     * @param out
     * @param pout
     * @param in
     * @throws IOException
     */
    private void sendSong(String songpath, PrintWriter out, BufferedOutputStream pout, BufferedReader in) throws IOException {
        //convert the song to an array of bytes
        byte [] songinbyte  = new byte [(int)(new File(songpath).length())];
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(songpath));
        bis.read(songinbyte,0,songinbyte.length);
        bis.close();
            
        //write the length to receive
        out.println(String.valueOf(songinbyte.length));
        
        //wait to receive a confirmation phone is ready to receive the song
        String confirm=in.readLine();
        
        if(confirm.equals("READY")){
            //write the bytes to the phone (this is auto split into smaller packets)
            pout.write(songinbyte,0,songinbyte.length);
            System.out.println("Wrote song");
            pout.flush();
        }        
    }

}
