import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.StringEscapeUtils;


public class Desktop_Server {
    
    private static String musicDirectoryPath;
    private static String ffmpegEXElocation;
    private static String iTunesDataLibraryFile;
    
    public static void main(String[] args) throws IOException {
        //load params from ini file
        loadIniFile();
        
        boolean useiTunesDataLibraryFile=false;
        BufferedReader readituneslibrary = null;
        if(iTunesDataLibraryFile!=null && !iTunesDataLibraryFile.equals("")){
            useiTunesDataLibraryFile=true;
            readituneslibrary=new BufferedReader(new FileReader(iTunesDataLibraryFile));
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
                
                //find the songs filetype, and convert it if it needs to be converted
                String filetype=songpath.substring(songpath.lastIndexOf("."));
                if(!filetype.equals(".mp3") || useiTunesDataLibraryFile){
                    try{
                        String metadata="";
                        if(useiTunesDataLibraryFile){
                            metadata=iTunesInterface.scanForitunesMetadata(request,readituneslibrary);
                        }
                        conversion(songpath, metadata);
                        //change the file to point to the converted song
                        songpath="tempout.mp3";
                    }catch(IOException e){
                        e.printStackTrace();
                    }
                }
                
                //convert the song to an array of bytes
                byte [] songinbyte  = new byte [(int)(new File(songpath).length())];
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(songpath));
                bis.read(songinbyte,0,songinbyte.length);
                bis.close();
                    
                //TODO retry sending the song if we do not receive confirmation for both receive song length and song
                //write the length to receive
                out.println(String.valueOf(songinbyte.length));
                    //System.out.println("wrote song legth "+songinbyte.length);
                
                //wait to receive a confirmation phone is ready to receive the song
                String confirm=in.readLine();
                
                if(confirm.equals("READY")){
                    //write the bytes to the phone (this is auto split into smaller packets)
                    pout.write(songinbyte,0,songinbyte.length);
                        System.out.println("Wrote song");
                    pout.flush();
                }
                    
                request=in.readLine();
                break;
            }
            out.close();
            in.close();
            pout.close();
            readituneslibrary.close();            
            phone.close();
            System.out.println("Sync finished");
        }

        //androidConnection.close();
    }
    
    /**
     * Reads ini file. Exits program if not found.
     * @throws IOException
     */
    private static void loadIniFile() throws IOException {
        File inifile=new File("SongSyncInfo.ini");
        if(!inifile.exists()){
            System.err.println("Unable to find ini file.");
            System.exit(0);
        }
        BufferedReader initfileparams=new BufferedReader(new FileReader("SongSyncInfo.ini"));
        String line=initfileparams.readLine();
        while(line!=null){
            if(line.toLowerCase().contains("musicdirectorypath")){
                musicDirectoryPath=line.substring(19);
            }else if(line.toLowerCase().contains("ffmpegexelocation")){
                ffmpegEXElocation=line.substring(18);
            }else if(line.toLowerCase().contains("itunesdatalibraryfile")){
                iTunesDataLibraryFile=line.substring(22);
            }
            line=initfileparams.readLine();
        }
        initfileparams.close();
    }

    /**
     * Convert the given audio file into the desired format. This method will block until ffmpeg finished converting.
     * Also add the metadata and album art if available
     * TODO allow to choose format
     * @param song the song to be converted
     * @param metadata 
     * @throws IOException 
     */
    private static void conversion(String song, String metadata) throws IOException {
        //convert the file to mp3 and add metadata + TODO keep existing metadata and preserving artwork
        String ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -ab 320000 -acodec libmp3lame "+metadata+"-y tempout.mp3";
        Runtime runtime = Runtime.getRuntime();
        Process p=runtime.exec(ffmpegcmmd);

        System.out.println("--------------metatdata & mp3--------------");
        wait_for_ffmpeg(p);
        
        //add the album art if it exists to the converted file
        File albumArt=new File("tempalbumart.png");
        if(albumArt.exists() && albumArt.isFile()){
            String ffmpegAddArt=ffmpegEXElocation+" -i tempout.mp3 -i tempalbumart.png -map 0:0 -map 1:0 -c copy -id3v2_version 3 -y tempout2.mp3";
            p=runtime.exec(ffmpegAddArt);
            
            System.out.println("--------------artwork--------------");
            wait_for_ffmpeg(p);
            
            //since we copied to a buffer file, delete original and rename buffer
            File orig=new File("tempout.mp3");
            orig.delete();
            new File("tempout2.mp3").renameTo(orig);
        }
        albumArt.delete();
    }
    
    /**
     * we have to wait a few seconds for ffmpeg to finish the conversion
     * read the command file until we read that it is finished
     * FIXME this is an ugly patch job, but it works. When we read no more text the process is finished. 
     */
    private static void wait_for_ffmpeg(Process p) throws IOException{
        InputStream in = p.getErrorStream();
        int c;
        while ((c = in.read()) != -1) {
          System.out.print((char) c);
        }
        in.close();
    }

    /**
     * Generate or update the list of all songs in the music directory
     * Every update just recreates the entire thing. Handles removed songs, added songs, stops accidental duplication.
     * To keep memory usage low, only call this when about to sync, and discard list when sync finishes.
     * @param locationpath the path of the current folder (changed for file tree recursion)
     * @param songfilenames a reference to the arraylist of songs
     */
    private static void generateList(ArrayList<String> songfilenames, String locationpath){
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
