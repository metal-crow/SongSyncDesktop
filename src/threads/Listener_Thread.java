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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import main.Desktop_Server;
import musicPlayerInterface.iTunesInterface;

import org.javatuples.Pair;


public class Listener_Thread extends Parent_Thread {
    private ServerSocket androidConnection;
    private boolean listen=true;
    
    public Listener_Thread(String musicDirectoryPath, String convertMusicTo,
            boolean useiTunesDataLibraryFile,
            RandomAccessFile readituneslibrary, String iTunesDataLibraryFile,
            String ffmpegEXElocation, String ffmpegCommand) {
        super(musicDirectoryPath, convertMusicTo, useiTunesDataLibraryFile, readituneslibrary, iTunesDataLibraryFile, ffmpegEXElocation, ffmpegCommand);
    }
    
    public void stop_connection(){
        listen=false;
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
            Desktop_Server.gui.current_status("Listening on port "+androidConnection.getLocalPort()+" at host "+InetAddress.getLocalHost().getHostAddress()+"\n",1);
        }catch(IOException e){
            Desktop_Server.gui.current_status("Socket Creation Failure.\n",1);
            e.printStackTrace();
            listen=false;
            return;
        }
        
        //loop and listen for connection
        while(listen){
            try{
                Socket phone = androidConnection.accept();
                Desktop_Server.gui.current_status("Sync Connection Recived.\n",2);
                
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
                Desktop_Server.gui.progress_max(songs.size());
                songs=null;//gc this, huge list of strings that we no longer need
                
                //recieve the request list from the phone and send over each song per request
                String request=in.readLine();
                while(request!=null && !request.equals("END OF SONG DOWNLOADS")){
                    try{
                        String songpath=convertSong(request);
                        sendSong(songpath, out, pout, in);
                        //clean up tmp file
                        new File(songpath).delete();
                        Desktop_Server.gui.progress_update();
                    }catch(IOException | InterruptedException e){
                        e.printStackTrace();
                        Desktop_Server.gui.progress_text("Converion failure for "+request+"\n");
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
                Desktop_Server.gui.current_status("Sync finished.\n",2);
            }catch(Exception e){
                if(listen){
                    Desktop_Server.gui.current_status("Unrecoverable network/file io error.\n",2);
                    e.printStackTrace();
                }
            }
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
            Desktop_Server.gui.progress_text("Wrote song\n");
            pout.flush();
        }        
    }

}
