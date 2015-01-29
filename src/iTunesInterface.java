import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.StringEscapeUtils;


public class iTunesInterface {

    /**
     * If using the itunes library, every song must have itunes metadata manually added to it because the user may have added metatdata to itunes. Same with album art.
     * Scan the library for metadata for the requested file, and return an ffmpeg valid string with the data.
     * Additionally, grab the album art and write it to the local directory as tempalbumart.png
     * @param song
     * @param readituneslibrary
     * @return
     * @throws IOException 
     */
    public static String scanForitunesMetadata(String song,BufferedReader readituneslibrary) throws IOException {
        boolean songfound=false;
        String Library_Persistent_ID="";
        while(readituneslibrary.ready() && !songfound){
            String line=readituneslibrary.readLine();
            //read each segment of data in the library, marking the start.
            //because we cannot tell at the head of the segment if this is our song, we need to mark to return if we find it is
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
            //grab the library id for the path
            if(line.contains("<key>Library Persistent ID</key>")){
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
                
                if(metadataline.contains("<key>Name</key>")){
                    metadata.append("-metadata title=\""+metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"))+"\" ");
                }
                else if(metadataline.contains("<key>Artist</key>")){
                    metadata.append("-metadata artist=\""+metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"))+"\" ");
                }
                else if(metadataline.contains("<key>Album</key>")){
                    metadata.append("-metadata album=\""+metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"))+"\" ");
                }
                else if(metadataline.contains("<key>Genre</key>")){
                    metadata.append("-metadata genre=\""+metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"))+"\" ");
                }
                else if(metadataline.contains("<key>Persistent ID</key>")){
                    String albumartlocation=metadataline.substring(metadataline.indexOf("<string>")+8, metadataline.indexOf("</string>"));
                    //see http://stackoverflow.com/questions/13795842/linking-itunes-itc2-files-and-ituneslibrary-xml for how i get the path from itunes's proprietary formula.
                    String firstfolder=String.format("%02d", Integer.parseInt(albumartlocation.substring(albumartlocation.length()-1),16));
                    String secondfolder=String.format("%02d", Integer.parseInt(albumartlocation.substring(albumartlocation.length()-2,albumartlocation.length()-1),16));
                    String thirdfolder=String.format("%02d", Integer.parseInt(albumartlocation.substring(albumartlocation.length()-3,albumartlocation.length()-2),16));
                    
                    String musicDirectoryPath="E:/iTunes";//XXX
                    File pathtToITC2ArtFile=new File(musicDirectoryPath+"/Album Artwork/Cache/"+Library_Persistent_ID+"/"+firstfolder+"/"+secondfolder+"/"+thirdfolder);

                    //make sure that the path and ITC2 file exists
                    if(pathtToITC2ArtFile.exists() && pathtToITC2ArtFile.list().length!=0){
                        System.out.println("Found art");
                        extractPNGfromITC2(new FileInputStream(pathtToITC2ArtFile.getPath()+"/"+pathtToITC2ArtFile.list()[0]));
                    }else{
                        System.err.println("Error, unable to find album artwork or a parent folder of the artwork.");
                    }
                    
                }
                if(metadataline.contains("<key>Location</key>")){
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
    
    
}
