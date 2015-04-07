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

import org.javatuples.Pair;


public class Wifi_Thread extends Thread {

    private String musicDirectoryPath;
    private String convertMusicTo;
    private boolean useiTunesDataLibraryFile;
    private RandomAccessFile readituneslibrary;
    private String iTunesDataLibraryFile;
    private ServerSocket androidConnection;
    
    public Wifi_Thread(String musicDirectoryPath, String convertMusicTo, boolean useiTunesDataLibraryFile, RandomAccessFile readituneslibrary, String iTunesDataLibraryFile) {
        this.musicDirectoryPath = musicDirectoryPath;
        this.convertMusicTo = convertMusicTo;
        this.useiTunesDataLibraryFile = useiTunesDataLibraryFile;
        this.readituneslibrary = readituneslibrary;
        this.iTunesDataLibraryFile = iTunesDataLibraryFile;
    }
    
    public void stop_connection(){
        try {
            androidConnection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try{
            //open server socket
            androidConnection=new ServerSocket(9091);
            System.out.println("Listening on port "+androidConnection.getLocalPort()+" at host "+androidConnection.getInetAddress().getHostName());
            
            //loop and listen for connection
            while(Desktop_Server.listen){
                Socket phone = androidConnection.accept();
                System.out.println("Connection get!");
                
                PrintWriter out=new PrintWriter(new OutputStreamWriter(phone.getOutputStream(), "utf-8"), true);//writer for song names/length
                BufferedReader in=new BufferedReader(new InputStreamReader(phone.getInputStream(), "utf-8"));//listener for phone requests/info
                BufferedOutputStream pout=new BufferedOutputStream(phone.getOutputStream());//writer for the song bytes

                //write out sync type(normal "N", full resync "R")
                out.println(Desktop_Server.sync_type);
                Desktop_Server.sync_type="N";//change back sync type to normal, now that we've done a full resync
                
                //generate the list of all songs
                ArrayList<String> songs=new ArrayList<String>();
                Desktop_Server.generateList(songs, musicDirectoryPath);
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
                    String songpath=musicDirectoryPath+request;
                    System.out.println("got request for "+request);
                    
                    //find the songs filetype, and convert it if it needs to be converted
                    String filetype=songpath.substring(songpath.lastIndexOf("."));
                    //if we're using iTunes we always need to remux to add iTunes metadata and art
                    if((!convertMusicTo.equals("") && !filetype.equals(convertMusicTo)) || useiTunesDataLibraryFile){
                            String metadata="";
                            if(useiTunesDataLibraryFile){
                                metadata=iTunesInterface.scanForitunesMetadata(request,readituneslibrary,iTunesDataLibraryFile);
                            }
                            try {
                                Desktop_Server.conversion(songpath, metadata);
                            } catch (IOException e) {
                                System.err.println(e.getMessage()+". Aborting sync.");
                                out.close();
                                in.close();
                                pout.close();       
                                phone.close();
                                System.exit(0);
                            }
                            //change the file to point to the converted song
                            songpath="tempout.mp3";
                    }
                    
                    //convert the song to an array of bytes
                    byte [] songinbyte  = new byte [(int)(new File(songpath).length())];
                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(songpath));
                    bis.read(songinbyte,0,songinbyte.length);
                    bis.close();
                        
                    //TODO retry sending the song if we do not receive confirmation for both receive song length and song
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
                ArrayList<Pair<String, ArrayList<String>>> playlists=iTunesInterface.generateM3UPlaylists(readituneslibrary);
                for(Pair<String,ArrayList<String>> playlist:playlists){
                    out.println(playlist.getValue0());//write playlist name
                    //write all songs in playlist
                    for(String song:playlist.getValue1()){
                        out.println(song);
                    }
                    out.println("NEW LIST");
                }
                out.println("NO MORE PLAYLISTS");
                
                out.close();
                in.close();
                pout.close();       
                phone.close();
                System.out.println("Sync finished");
            }
            
            androidConnection.close();
            
        }catch(SocketException e){
            System.out.println("Wifi Thread closed.");
        }catch(IOException e){
            System.err.println("Unrecoverable network/file io error.");
            e.printStackTrace();
        }
    }
}
