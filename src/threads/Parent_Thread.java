package threads;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;

import musicPlayerInterface.iTunesInterface;


public class Parent_Thread extends Thread {

    protected String musicDirectoryPath;
    protected String convertMusicTo;
    private boolean useiTunesDataLibraryFile;
    protected RandomAccessFile readituneslibrary;
    private String iTunesDataLibraryFile;
    private String ffmpegEXElocation;
    private String ffmpegCommand;
    private HashMap<String,String> codecs;
    
    public Parent_Thread(String musicDirectoryPath, String convertMusicTo,
            boolean useiTunesDataLibraryFile,
            RandomAccessFile readituneslibrary, String iTunesDataLibraryFile,
            String ffmpegEXElocation, String ffmpegCommand,
            HashMap<String, String> codecs) {
        this.musicDirectoryPath = musicDirectoryPath;
        this.convertMusicTo = convertMusicTo;
        this.useiTunesDataLibraryFile = useiTunesDataLibraryFile;
        this.readituneslibrary = readituneslibrary;
        this.iTunesDataLibraryFile = iTunesDataLibraryFile;
        this.ffmpegEXElocation = ffmpegEXElocation;
        this.ffmpegCommand = ffmpegCommand;
        this.codecs = codecs;
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
        System.out.println("got request for "+request);
        
        //find the songs filetype, and convert it if it needs to be converted
        String filetype=songpath.substring(songpath.lastIndexOf("."));
        //if we're using iTunes we always need to remux to add iTunes metadata and art
        if((!convertMusicTo.equals("") && !filetype.equals(convertMusicTo)) || useiTunesDataLibraryFile){
                String metadata="";
                if(useiTunesDataLibraryFile){
                    metadata=iTunesInterface.scanForitunesMetadata(request,readituneslibrary,iTunesDataLibraryFile);
                }
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
            ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -ab 320000 -acodec "+codecs.get(convertMusicTo)+" -id3v2_version 3 -map_metadata 0 "+metadata+"-y tempout"+convertMusicTo;
        }
        //only convert
        else if(convert && !remux){
            ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -ab 320000 -acodec "+codecs.get(convertMusicTo)+"-y tempout"+convertMusicTo;
        }
        //only remux
        else if(!convert && remux){
            ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -id3v2_version 3 -map_metadata 0 "+metadata+"-y tempout"+convertMusicTo;
        }
        //overwrite for user specified command
        if(ffmpegCommand!=null){
            ffmpegcmmd=ffmpegEXElocation+" "+ffmpegCommand;
        }
        Runtime runtime = Runtime.getRuntime();
        Process p=runtime.exec(ffmpegcmmd);
        
        p.waitFor();
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
            runtime.exec(ffmpegArtExtract).waitFor();
            
            if(p.exitValue()!=0 && p.exitValue()!=1){
                throw new IOException("Failure in extracting album art");
            }
        }
         
        //if the song have album art, if not, just skip this
        if(albumArt.exists() && albumArt.isFile() && albumArt.length()>0){
            String ffmpegAddArt=ffmpegEXElocation+" -i tempout"+convertMusicTo+" -i tempalbumart.jpg -map 0:0 -map 1:0 -c copy -id3v2_version 3 -y tempout2"+convertMusicTo;
            runtime.exec(ffmpegAddArt).waitFor();
            
            if(p.exitValue()!=0){
                throw new IOException("Failure in adding album art");
            }
            
            //since we copied to a buffer file, delete original and rename buffer
            File orig=new File("tempout"+convertMusicTo);
            orig.delete();
            new File("tempout2"+convertMusicTo).renameTo(orig);
            albumArt.delete();
        }
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
                songfilenames.add(f.getPath().substring(musicDirectoryPath.length()));
            }else{
                generateList(songfilenames, f.getPath());
            }
        }
    }

}
