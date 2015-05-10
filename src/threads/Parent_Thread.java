package threads;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import musicPlayerInterface.iTunesInterface;


public class Parent_Thread extends Thread {
    //DEBUG
    private static final boolean print_cmd_output=false;
    private static final boolean write_song_list=false;
    
    protected String musicDirectoryPath;
    protected String convertMusicTo;
    protected boolean useiTunesDataLibraryFile;
    protected RandomAccessFile readituneslibrary;
    private String iTunesDataLibraryFile;
    private String ffmpegEXElocation;
    private String ffmpegCommand;
    
    public Parent_Thread(String musicDirectoryPath, String convertMusicTo,
            boolean useiTunesDataLibraryFile,
            RandomAccessFile readituneslibrary, String iTunesDataLibraryFile,
            String ffmpegEXElocation, String ffmpegCommand) {
        this.musicDirectoryPath = musicDirectoryPath;
        this.convertMusicTo = convertMusicTo;
        this.useiTunesDataLibraryFile = useiTunesDataLibraryFile;
        this.readituneslibrary = readituneslibrary;
        this.iTunesDataLibraryFile = iTunesDataLibraryFile;
        this.ffmpegEXElocation = ffmpegEXElocation;
        this.ffmpegCommand = ffmpegCommand;
    }
    
    /**
     * Remove song from local music file system
     * @param song
     */
    protected void removeSong(String song){
        System.out.println("Song "+song+"being removed from music database.");
        File song_to_delete=new File(musicDirectoryPath+song);
        boolean deleted=song_to_delete.delete();
        if(!deleted){
            System.err.println("Error deleting song "+song);
        }
    }
    
    /**
     * Given song location, check if needs to be converted, convert, point to converted/song location
     * @param request
     * @return
     * @throws IOException
     * @throws InterruptedException 
     */
    protected String convertSong(String request) throws IOException, InterruptedException {
        String songpath=musicDirectoryPath+request;
        System.out.print("Got request for "+request);
        
        //find the songs filetype, and convert it if it needs to be converted
        String filetype=songpath.substring(songpath.lastIndexOf("."));
        //if we're using iTunes we always need to remux to add iTunes metadata and art
        if((!convertMusicTo.equals("") && !filetype.equals(convertMusicTo)) || useiTunesDataLibraryFile){
                String metadata="";
                if(useiTunesDataLibraryFile){
                    metadata=iTunesInterface.scanForitunesMetadata(request,readituneslibrary,iTunesDataLibraryFile);
                }
                System.out.print(" Converting song");
                conversion(songpath, metadata);
                //change the file to point to the converted song
                songpath="tempout"+convertMusicTo;
        }
        
        return songpath;
    }

    /**
     * Convert the given audio file into the desired format. This method will block until ffmpeg finished converting.
     * Also add the metadata and album art if available
     * @param song the song to be converted
     * @param metadata 
     * @throws IOException 
     * @throws InterruptedException 
     */
    protected void conversion(String song, String metadata) throws IOException, InterruptedException {
        String ffmpegcmmd = null;
        //if the song isn't native filetype, we need to convert it
        boolean convert=!song.substring(song.lastIndexOf('.')).equals(convertMusicTo);
        //if we are using itunes, we need to remux file with metadata
        boolean remux=!metadata.equals("");
        
        //convert the file and remux
        if(convert && remux){
            ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -q:a 0 -id3v2_version 3 -map_metadata 0 "+metadata+"-y tempout"+convertMusicTo;
        }
        //only convert
        else if(convert && !remux){
            ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -q:a 0 -y tempout"+convertMusicTo;
        }
        //only remux
        else if(!convert && remux){
            ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -id3v2_version 3 -map_metadata 0 "+metadata+"-y tempout"+convertMusicTo;
        }
        //else just move to the tempout location
        else{
            ffmpegcmmd="cmd.exe /c copy \""+song.replaceAll("/", "\\\\")+"\" /y tempout"+convertMusicTo;
        }
        //overwrite for user specified command
        if(ffmpegCommand!=null){
            ffmpegcmmd=ffmpegEXElocation+" "+ffmpegCommand;
        }
        
        Runtime runtime = Runtime.getRuntime();
        if(print_cmd_output){
            System.out.println(ffmpegcmmd);
        }
        Process p=runtime.exec(ffmpegcmmd);
        
        listen_process(p);
        
        if(p.exitValue()!=0){
            throw new IOException("Failure in adding metadata");
        }
        
        //add the album art to the converted file
        //NOTE: after various testing, any number of errors can occur in keeping the art. Additionally, i dont know how iTunes handles album art changes.
        //Therefore, im just always going to extract art to add, instead of sometimes relying on ffmpeg to keep it. 
        File albumArt=new File("tempalbumart.jpg");
        
        //if we didnt pull the album art from itunes, we need to ensure that we correctly add album art.
        //ffmpeg will sometimes throw errors and not include the album art if the embedded art is incorrectly formatted
        if(!(albumArt.exists() && albumArt.isFile() && albumArt.length()>0)){
            //extract the art from the original file
            String ffmpegArtExtract=ffmpegEXElocation+" -i \""+song+"\" -an -vcodec copy -y tempalbumart.jpg";
            p=runtime.exec(ffmpegArtExtract);//.waitFor();
            listen_process(p);
            
            if(p.exitValue()!=0 && p.exitValue()!=1){
                throw new IOException("Failure in extracting album art");
            }
        }
         
        //if the song have album art, if not, just skip this
        if(albumArt.exists() && albumArt.isFile() && albumArt.length()>0){
            String ffmpegAddArt=ffmpegEXElocation+" -i tempout"+convertMusicTo+" -i tempalbumart.jpg -map 0:0 -map 1:0 -c copy -id3v2_version 3 -y tempout2"+convertMusicTo;
            p=runtime.exec(ffmpegAddArt);//.waitFor();
            listen_process(p);
            
            if(p.exitValue()!=0){
                throw new IOException("Failure in adding album art");
            }
            
            //since we copied to a buffer file, delete original and rename buffer
            File orig=new File("tempout"+convertMusicTo);
            orig.delete();
            new File("tempout2"+convertMusicTo).renameTo(orig);
        }
        albumArt.delete();
    }
    
    /**
     * This is only used for debugging purposes, and should be inserted into code to test.
     * @param p
     * @throws IOException
     */
    private static void listen_process(Process p) throws IOException{
        InputStream in = p.getErrorStream();
        int c;
        while ((c = in.read()) != -1) {
            if(print_cmd_output){
                System.out.print((char) c);
            }
        }
        in.close();
    }

    /**
     * Generate or update the list of all songs in the music directory
     * Every update just recreates the entire thing. Handles removed songs, added songs, stops accidental duplication.
     * To keep memory usage low, only call this when about to sync, and discard list when sync finishes.
     * @param locationpath the path of the current folder (changed for file tree recursion)
     * @param songfilenames a reference to the arraylist of songs (want external reference because this method recurses)
     */
    protected void generateList(ArrayList<String> songfilenames, String locationpath){
        File folder = new File(locationpath);
        
        for(File f:folder.listFiles()){
            //i could check if this is a music file, but i might forget a file type
            //im just going to assume the filestucture is all music files
            if(f.isFile()){
                //we only want the music filesystem structure path, so remove the musicDirectoryPath bit
                String file=f.getPath().substring(musicDirectoryPath.length());
                file=file.replaceAll("\\\\", "/");
                songfilenames.add(file);
            }else{
                generateList(songfilenames, f.getPath());
            }
        }
        
        //DEBUG in case we want the entire song list early
        if(write_song_list){
            try{
                BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream("SongSync_Song_List.txt.dbg"), "utf-8"));
                for(String s:songfilenames){
                    out.write(s+"\n");
                }
                out.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

}
