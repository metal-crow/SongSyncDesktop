import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;


public class Desktop_Server {
    
    private static String musicDirectoryPath;
    private static String ffmpegEXElocation;
    private static String iTunesDataLibraryFile;
    private static String convertMusicTo;
    private static String ffmpegCommand;
    private static final boolean debugFFmpeg=false;
    public volatile static boolean listen=true;//thread's listen to know when to end
    public volatile static String sync_type="N";//what type of sync we're doing. Need threads to be able to edit
    //map codes to file extensions
    private static HashMap<String,String> codecs=new HashMap<String,String>();
    static{
        codecs.put(".mp3", "libmp3lame");
    }
    
    public static void main(String[] args) throws IOException {
        //load params from ini file
        try {
            loadIniFile();
        } catch (IOException e1) {
            System.err.println("Ini file is invalid or does not exist.");
            System.exit(0);
        }
        
        //open itunes reader if ini file has it
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
        
        //start wifi connection listener thread
        Wifi_Thread wifi=new Wifi_Thread(musicDirectoryPath, convertMusicTo, useiTunesDataLibraryFile, readituneslibrary, iTunesDataLibraryFile);
        wifi.start();
        //start usb connection listener thread
        
        String userend="";
        Scanner in=new Scanner(System.in);
        System.out.println("Do you want to end the server? Y/N");
        while(!userend.equals("N")){
            userend=in.next();
            if(userend.equals("N")){
                listen=false;
            }
        }
        
        in.close();
        readituneslibrary.close();
    }
    
    /**
     * Reads ini file. Exits program if not found.
     * @throws IOException
     */
    private static void loadIniFile() throws IOException {
        File inifile=new File("SongSyncInfo.ini");
        File inifile_check=new File("SongSyncInfo.ini.tmp");
        if(!inifile.exists()){
            System.err.println("Unable to find ini file.");
            System.exit(0);
        }
        if(!inifile_check.exists()){
            Files.copy(inifile.toPath(), inifile_check.toPath());
        }
        BufferedReader initfileparams=new BufferedReader(new FileReader(inifile));
        //older ini file copy to check for changes. Read simultaneously, and when w read data check if it differs on old one.
        BufferedReader chk_tmp_initfileparams=new BufferedReader(new FileReader(inifile_check));

        String line=initfileparams.readLine();
        String tmp_line=chk_tmp_initfileparams.readLine();
        while(line!=null){
            if(!line.contains("#")){
                if(line.toLowerCase().contains("musicdirectorypath")){
                    musicDirectoryPath=line.substring(19);
                    if(!tmp_line.substring(19).equals(musicDirectoryPath)){
                        sync_type="R";
                    }
                }else if(line.toLowerCase().contains("ffmpegexelocation")){
                    ffmpegEXElocation=line.substring(18);
                    if(!tmp_line.substring(18).equals(ffmpegEXElocation)){
                        sync_type="R";
                    }
                }else if(line.toLowerCase().contains("itunesdatalibraryfile")){
                    iTunesDataLibraryFile=line.substring(22);
                    if(!tmp_line.substring(22).equals(iTunesDataLibraryFile)){
                        sync_type="R";
                    }
                }else if(line.toLowerCase().contains("convertsongsto")){
                    convertMusicTo=line.substring(15);
                    if(!tmp_line.substring(15).equals(convertMusicTo)){
                        sync_type="R";
                    }
                }else if(line.toLowerCase().contains("ffmpegcommand") && line.substring(14).length()>1){
                    ffmpegCommand=line.substring(14);
                    if(!tmp_line.substring(14).equals(ffmpegCommand)){
                        sync_type="R";
                    }
                }
            }
            line=initfileparams.readLine();
            tmp_line=chk_tmp_initfileparams.readLine();
        }
        initfileparams.close();
        chk_tmp_initfileparams.close();
        //make copy to verify against new changes now that we know what has changed for this session
        Files.copy(inifile.toPath(), inifile_check.toPath());
    }

    /**
     * Convert the given audio file into the desired format. This method will block until ffmpeg finished converting.
     * Also add the metadata and album art if available
     * @param song the song to be converted
     * @param metadata 
     * @throws IOException 
     */
    public static void conversion(String song, String metadata) throws IOException {
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

        wait_for_ffmpeg(p);
        //p.waitFor();
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
            p=runtime.exec(ffmpegArtExtract);
            wait_for_ffmpeg(p);
            if(p.exitValue()!=0 && p.exitValue()!=1){
                throw new IOException("Failure in extracting album art");
            }
        }
         
        //if the song have album art, if not, just skip this
        if(albumArt.exists() && albumArt.isFile() && albumArt.length()>0){
            String ffmpegAddArt=ffmpegEXElocation+" -i tempout"+convertMusicTo+" -i tempalbumart.jpg -map 0:0 -map 1:0 -c copy -id3v2_version 3 -y tempout2"+convertMusicTo;
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
          if(debugFFmpeg){
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
