import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;


public class Desktop_Server {
    
    private static String musicDirectoryPath;
    private static String ffmpegEXElocation;
    private static String iTunesDataLibraryFile;
    
    public static void main(String[] args) throws IOException {
        //load params from ini file
        File inifile=new File("SongSyncInfo.ini");
        if(!inifile.exists()){
            System.err.println("Unable to find ini file.");
            System.exit(0);
        }
        BufferedReader initfileparams=new BufferedReader(new FileReader("SongSyncInfo.ini"));
        String line=initfileparams.readLine();
        while(line!=null){
            if(line.toLowerCase().contains("musicdirectorypath")){
                musicDirectoryPath=line.substring(20);
            }else if(line.toLowerCase().contains("ffmpegexelocation")){
                ffmpegEXElocation=line.substring(19);
            }else if(line.toLowerCase().contains("itunesdatalibraryfile")){
                iTunesDataLibraryFile=line.substring(23);
            }
            line=initfileparams.readLine();
        }
        initfileparams.close();
        
        boolean useiTunesDataLibraryFile=false;
        if(iTunesDataLibraryFile!=null && !iTunesDataLibraryFile.equals("")){
            useiTunesDataLibraryFile=true;
        }
        
        
        ServerSocket androidConnection=new ServerSocket(9091);
        System.out.println("Listening on port "+androidConnection.getLocalPort()+" at host "+androidConnection.getInetAddress().getHostName());
        
        //loop and listen for connection
        while(true){
            Socket phone = androidConnection.accept();
            System.out.println("Connection get!");
            
            //generate the list of all songs
            ArrayList<String> songs=new ArrayList<String>();
            generateList(songs, musicDirectoryPath);
            
            //write the current song list to the phone
            PrintWriter out=new PrintWriter(phone.getOutputStream(), true);
            for(String song:songs){
                out.println(song);
            }
            //tell phone it is done writing song list
            out.println("ENDOFLIST");
            
            //recieve the request list from the phone and send over each song per request
            BufferedReader in=new BufferedReader(new InputStreamReader(phone.getInputStream()));
            BufferedOutputStream pout=new BufferedOutputStream(phone.getOutputStream());
            
            String request=in.readLine();
            while(request!=null){
                String songpath=musicDirectoryPath+request;
                    System.out.println("got request for "+request);
                File song = new File (songpath);
                
                //find the songs filetype, and convert it if it needs to be converted
                String filetype=songpath.substring(songpath.lastIndexOf("."));
                if(!filetype.equals(".mp3")){
                    try{
                        conversion(song);
                        //change the file to point to the converted song
                        song=new File("tempout.mp3");
                    }catch(IOException e){
                        e.printStackTrace();
                    }
                }
                
                //TODO add itunes metadata from its library file to mp3
                if(useiTunesDataLibraryFile){
                    
                }
                
                //convert the song to an array of bytes
                byte [] songinbyte  = new byte [(int)song.length()];
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(song));
                bis.read(songinbyte,0,songinbyte.length);
                bis.close();
                    
                //TODO retry sending the song if we do not receive confirmation for both receive song length and song
                //write the length to receive
                out.println(String.valueOf(songinbyte.length));
                    //System.out.println("wrote song legth "+songinbyte.length);
                    
                //wait to receive ready confirmation
                String confirmlength=in.readLine();
                    //System.out.println(confirmlength);
                
                
                //write the bytes to the phone (this is auto split into smaller packets)
                if(confirmlength.equals("READY")){
                    pout.write(songinbyte,0,songinbyte.length);
                        System.out.println("Wrote song");
                    pout.flush();
                }
                    
                request=in.readLine();
            }
            out.close();
            in.close();
            pout.close();
            
            phone.close();
            System.out.println("Sync finished");
        }

        //androidConnection.close();
    }
    
    /**
     * Convert the given audio file into the desired format
     * @param song the song to be converted
     * @throws IOException 
     */
    public static void conversion(File song) throws IOException {
        String command=ffmpegEXElocation+" -i "+song.getPath()+" -ab 320000 -acodec libmp3lame tempout.mp3";
        Runtime runtime = Runtime.getRuntime();
        runtime.exec(command);
    }

    /**
     * Generate or update the list of all songs in the music directory
     * Every update just recreates the entire thing. Handles removed songs, added songs, stops accidental duplication.
     * To keep memory usage low, only call this when about to sync, and discard list when sync finishes.
     * @param locationpath the path of the current folder (changed for file tree recursion)
     * @param songfilenames a reference to the arraylist of songs
     */
    public static void generateList(ArrayList<String> songfilenames, String locationpath){
        File folder = new File(locationpath);
        
        for(File f:folder.listFiles()){
            //i could check if this is a music file, but i might forget a file type
            //im just going to assume the filestucture is all music files
            if(f.isFile()){
                //we only want the music filesystem structure path, so remove the musicDirectoryPath bit
                songfilenames.add(f.getPath().substring(musicDirectoryPath.length()));
            }else{
                generateList(songfilenames, f.getPath());
            }
        }
    }

}
