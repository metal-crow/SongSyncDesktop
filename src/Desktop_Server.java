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
                            metadata=scanForitunesMetadata(songpath,readituneslibrary);
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
     * If using the itunes library, every song must have itunes metadata manually added to it because the user may have added metatdata to itunes. Same with album art.
     * Scan the library for metadata for the requested file, and return an ffmpeg valid string with the data.
     * Additionally, grab the album art and write it to the local directory as tempalbumart.png
     * @param song
     * @param readituneslibrary
     * @return
     * @throws IOException 
     */
    private static String scanForitunesMetadata(String song,BufferedReader readituneslibrary) throws IOException {
        boolean songfound=false;
        String Library_Persistent_ID="";
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
            else if(line.contains("Library_Persistent_ID")){
                Library_Persistent_ID=line.substring(line.indexOf("<string>")+8, line.indexOf("</string>"));
            }
        }
        //now scan the header for the metadata
        StringBuilder metadata=new StringBuilder();
        if(songfound){
            //i cant do this blind because sometimes a tag isnt there
            String metadataline="";
            boolean endofsongmetadata=false;
            while(!endofsongmetadata){
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
                else if(metadataline.contains("Persistent ID")){
                    String albumartlocation=metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"));
                    //see http://stackoverflow.com/questions/13795842/linking-itunes-itc2-files-and-ituneslibrary-xml for how i get the path from itunes's proprietary formula.
                    String firstfolder=Integer.toHexString(Integer.valueOf(albumartlocation.substring(albumartlocation.length())));
                    String secondfolder=Integer.toHexString(Integer.valueOf(albumartlocation.substring(albumartlocation.length()-1,albumartlocation.length())));
                    String thirdfolder=Integer.toHexString(Integer.valueOf(albumartlocation.substring(albumartlocation.length()-2,albumartlocation.length()-1)));
                    
                    File pathtToITC2ArtFile=new File(musicDirectoryPath+"/Album Artwork/Cache/"+Library_Persistent_ID+"/"+firstfolder+"/"+secondfolder+"/"+thirdfolder);
                    //make sure that the path and ITC2 file exists
                    if(pathtToITC2ArtFile.exists() && pathtToITC2ArtFile.list().length!=0){
                        extractPNGfromITC2(new FileInputStream(pathtToITC2ArtFile.list()[0]));
                    }else{
                        System.err.println("Error, unable to find album artwork or a parent folder of the artwork.");
                    }
                    
                }
                else if(metadataline.contains("Location")){
                    endofsongmetadata=true;
                }
            }
            
        }
        return metadata.toString();
    }
    
    /**
     * Decypher the ITC2 file, graph the png album art from it, and write it to the local directory as tempalbumart.png
     * See http://nada-labs.net/2010/file-format-reverse-engineering-an-introduction/comment-page-1/
     * @param in
     * @throws IOException
     */
    private static void extractPNGfromITC2(FileInputStream in) throws IOException{
        FileOutputStream out=new FileOutputStream("tempalbumart.png");
        boolean writeout=false;
        int headersfound=0;
        int c;
        byte[] bufferforcheck=new byte[8];
        byte[] headerstart={(byte)0,(byte)0,(byte)0,(byte)100,(byte)97,(byte)116,(byte)97,(byte)137};//the png header. The last byte is the real png starting byte
        byte[] pngend={(byte)69,(byte)78,(byte)68,(byte)174,(byte)66,(byte)96,(byte)130,(byte)0};//the end of the png file
        
        while ((c = in.read()) != -1) {
            //shift the buffer 1 byte left
            for(int i=1;i<8;i++){
                bufferforcheck[i-1]=bufferforcheck[i];
            }
            //append new byte to end of the buffer
            bufferforcheck[7]=(byte)c;
            
            //check the buffer if it matches the header
            if(Arrays.equals(bufferforcheck, headerstart)){
                writeout=true;
                headersfound++;
            }
            //check the buffer if it matched the end of the png file
            else if(Arrays.equals(bufferforcheck, pngend)){
                writeout=false;
            }
            //for pngs the 3rd png header indicates the largest sized png
            if(writeout && headersfound==3){
                out.write(c);
            }
        }
        out.close();
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
        //TODO always delete the album art png after finished converting to prevent another song from accidentally having the wrong album art applied to it
        String ffmpegcmmd=ffmpegEXElocation+" -i \""+song+"\" -ab 320000 -acodec libmp3lame "+metadata+"-y tempout.mp3";
        Runtime runtime = Runtime.getRuntime();
        Process p=runtime.exec(ffmpegcmmd);

        //we have to wait a few seconds for ffmpeg to finish the conversion
        //read the command file until we read that it is finished
        //FIXME this is an ugly patch job, but it works. When we read no more text the process is finished. 
        InputStream in = p.getInputStream();
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
