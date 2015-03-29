import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import org.javatuples.Pair;


public class Desktop_Server {
    
    private static String musicDirectoryPath;
    private static String ffmpegEXElocation;
    private static String iTunesDataLibraryFile;
    private static String convertMusicTo;
    private static String ffmpegCommand;
    
    public static void main(String[] args) throws IOException {
        //load params from ini file
        try {
            loadIniFile();
        } catch (IOException e1) {
            System.err.println("Ini file is invalid or does not exist.");
            System.exit(0);
        }
        
        boolean useiTunesDataLibraryFile=false;
        RandomAccessFile readituneslibrary = null;
        if(iTunesDataLibraryFile!=null && !iTunesDataLibraryFile.equals("")){
            useiTunesDataLibraryFile=true;
            try{
                readituneslibrary=new RandomAccessFile(iTunesDataLibraryFile, "r");
            }catch(FileNotFoundException e){
                System.err.println("Invalid iTunes library location");
                System.exit(0);
            }
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
            PrintWriter out=new PrintWriter(new OutputStreamWriter(phone.getOutputStream(), "utf-8"), true);

            //write the filetype of the songs
            out.println(convertMusicTo);
            
            for(String song:songs){
                out.println(song);
            }
            //tell phone it is done writing song list
            out.println("ENDOFLIST");
            
            //recieve the request list from the phone and send over each song per request
            BufferedReader in=new BufferedReader(new InputStreamReader(phone.getInputStream(), "utf-8"));
            BufferedOutputStream pout=new BufferedOutputStream(phone.getOutputStream());
            
            String request=in.readLine();
            while(request!=null && !request.equals("END OF SONG DOWNLOADS")){
                String songpath=musicDirectoryPath+request;
                System.out.println("got request for "+request);
                
                //find the songs filetype, and convert it if it needs to be converted
                String filetype=songpath.substring(songpath.lastIndexOf("."));
                //if we're using iTunes we always need to remux to add iTunes metadata and art
                if(!filetype.equals(convertMusicTo) || useiTunesDataLibraryFile){
                        String metadata="";
                        if(useiTunesDataLibraryFile){
                            metadata=iTunesInterface.scanForitunesMetadata(request,readituneslibrary,iTunesDataLibraryFile);
                        }
                        try {
                            conversion(songpath, metadata);
                        } catch (IOException e) {
                            System.err.println("FFmpeg command is invalid/malformed. Aborting sync.");
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
                out.println(playlist.getValue0());
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


        //readituneslibrary.close();     
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
            if(!line.contains("#")){
                if(line.toLowerCase().contains("musicdirectorypath")){
                    musicDirectoryPath=line.substring(19);
                }else if(line.toLowerCase().contains("ffmpegexelocation")){
                    ffmpegEXElocation=line.substring(18);
                }else if(line.toLowerCase().contains("itunesdatalibraryfile")){
                    iTunesDataLibraryFile=line.substring(22);
                }else if(line.toLowerCase().contains("convertsongsto")){
                    convertMusicTo=line.substring(15);
                }else if(line.toLowerCase().contains("ffmpegcommand") && line.substring(14).length()>1){
                    ffmpegCommand=line.substring(14);
                }
            }
            line=initfileparams.readLine();
        }
        initfileparams.close();
    }

    /**
     * Convert the given audio file into the desired format. This method will block until ffmpeg finished converting.
     * Also add the metadata and album art if available
     * TODO this can be optimized for various scenarios. Not doing that right now.
     * @param song the song to be converted
     * @param metadata 
     * @throws IOException 
     */
    private static void conversion(String song, String metadata) throws IOException {
        //convert the file and add metadata + keep existing metadata and trying to preserving artwork
        //TODO need to change conversion codec based on the chosen file extension
        String ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -ab 320000 -acodec libmp3lame -id3v2_version 3 -map_metadata 0 "+metadata+"-y tempout"+convertMusicTo;
        if(ffmpegCommand!=null){
            ffmpegcmmd=ffmpegEXElocation+" "+ffmpegCommand;
        }
        Runtime runtime = Runtime.getRuntime();
        Process p=runtime.exec(ffmpegcmmd);

        wait_for_ffmpeg(p);
        //p.waitFor();
        if(p.exitValue()!=0){
            throw new IOException("Failure in adding metadata");
        }
        
        //add the album art to the converted file
        //NOTE: after various testing, any number of errors can occur in keeping the art. Additionally, i dont know how iTunes handles album art changes.
        //Therefore, im just always going to extract art to add, instead of sometimes relying on ffmpeg to keep it. 
        File albumArt=new File("tempalbumart.png");
        
        //if we didnt pull the album art from itunes, we need to ensure that we correctly add album art.
        //ffmpeg will sometimes throw errors and not include the album art if the embedded art is incorrectly formatted
        if(!(albumArt.exists() && albumArt.isFile() && albumArt.length()>0)){
            //extract the art from the original file
            String ffmpegArtExtract=ffmpegEXElocation+" -i \""+song+"\" -an -vcodec copy tempalbumart.png";
            p=runtime.exec(ffmpegArtExtract);
            wait_for_ffmpeg(p);
            if(p.exitValue()!=0){
                throw new IOException("Failure in extracting album art");
            }
        }
         
        String ffmpegAddArt=ffmpegEXElocation+" -i tempout"+convertMusicTo+" -i tempalbumart.png -map 0:0 -map 1:0 -c copy -id3v2_version 3 -y tempout2"+convertMusicTo;
        p=runtime.exec(ffmpegAddArt);
        
        wait_for_ffmpeg(p);
        if(p.exitValue()!=0){
            throw new IOException("Failure in adding album art");
        }
        
        //since we copied to a buffer file, delete original and rename buffer
        File orig=new File("tempout"+convertMusicTo);
        orig.delete();
        new File("tempout2"+convertMusicTo).renameTo(orig);
        albumArt.delete();
    }
    
    /**
     * we have to wait a few seconds for ffmpeg to finish the conversion
     * read the command file until we read that it is finished
     * FIXME this is an ugly patch job, but it works. When we read no more text the process is finished. Can use p.waitFor()
     */
    private static void wait_for_ffmpeg(Process p) throws IOException{
        InputStream in = p.getErrorStream();
        int c;
        while ((c = in.read()) != -1) {
          //System.out.print((char) c);
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
