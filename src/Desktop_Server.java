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

import org.apache.commons.lang3.StringEscapeUtils;


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
                musicDirectoryPath=line.substring(19);
            }else if(line.toLowerCase().contains("ffmpegexelocation")){
                ffmpegEXElocation=line.substring(18);
            }else if(line.toLowerCase().contains("itunesdatalibraryfile")){
                iTunesDataLibraryFile=line.substring(22);
            }
            line=initfileparams.readLine();
        }
        initfileparams.close();
        
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
                if(!filetype.equals(".mp3")){
                    try{
                        String metadata="";
                        if(useiTunesDataLibraryFile){
                            metadata=scanForMetadata(songpath,readituneslibrary);
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
            readituneslibrary.close();            
            phone.close();
            System.out.println("Sync finished");
        }

        //androidConnection.close();
    }
    
    /**
     * Scan the library for metadata for the requested file, and return an ffmpeg valid string with the data
     * @param song
     * @param readituneslibrary
     * @return
     * @throws IOException 
     */
    private static String scanForMetadata(String song,BufferedReader readituneslibrary) throws IOException {
        boolean songfound=false;
        while(readituneslibrary.ready() && !songfound){
            String line=readituneslibrary.readLine();
            //read each segment of data in the library, marking the start.
            //because we cannot tell at the head of the segment if this is our song, we need ot mark to return if we find it is
            if(line.contains("<key>Track ID</key>")){
                readituneslibrary.mark(2000);//this should be enough to cover one header.
            }
            else if(line.contains("<key>Location</key>")){
                //this value will tell us if this header is our song
                String path=StringEscapeUtils.unescapeXml(line).replaceAll("%20", " ");//replace itunes escape chars
                if(path.contains(song)){
                    songfound=true;
                    readituneslibrary.reset();
                }
                                
            }
        }
        //now scan the header for the metadata
        StringBuilder metadata=new StringBuilder();
        if(songfound){
            //i cant do this blind because sometimes a tag isnt there
            String metadataline="";
            for(int i=0;i<4;i++){
                metadataline=StringEscapeUtils.unescapeXml(readituneslibrary.readLine());
                if(metadataline.contains("Name")){
                    metadata.append("-metadata title=\""+metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"))+"\" ");
                }
                else if(metadataline.contains("Artist")){
                    metadata.append("-metadata artist=\""+metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"))+"\" ");
                }
                else if(metadataline.contains("Album")){
                    metadata.append("-metadata album=\""+metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"))+"\" ");
                }
                else if(metadataline.contains("Genre")){
                    metadata.append("-metadata genre=\""+metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"))+"\" ");
                }
            }
            
        }
        return metadata.toString();
    }

    /**
     * Convert the given audio file into the desired format
     * TODO allow to choose format
     * @param song the song to be converted
     * @param metadata 
     * @throws IOException 
     */
    public static void conversion(String song, String metadata) throws IOException {
        String ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -ab 320000 -acodec libmp3lame "+metadata+"-y tempout.mp3";
        Runtime runtime = Runtime.getRuntime();
        runtime.exec(ffmpegcmmd);
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
