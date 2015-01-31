import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.StringEscapeUtils;
import org.javatuples.Pair;


public class iTunesInterface {

    /**
     * If using the itunes library, every song must have itunes metadata manually added to it because the user may have added metatdata to itunes. Same with album art.
     * Scan the library for metadata for the requested file, and return an ffmpeg valid string with the data.
     * Additionally, grab the album art and write it to the local directory as tempalbumart.png
     * @param song
     * @param readituneslibrary
     * @param musicDirectoryPath 
     * @return
     * @throws IOException 
     */
    public static String scanForitunesMetadata(String song,RandomAccessFile readituneslibrary, String musicDirectoryPath) throws IOException {
        boolean songfound=false;
        String Library_Persistent_ID="";
        long markpos = -1;
        String line = "";
        while(!songfound && line!=null){
            line=readituneslibrary.readLine();
            //read each segment of data in the library, marking the start.
            //because we cannot tell at the head of the segment if this is our song, we need to mark to return if we find it is
            if(line.contains("<key>Track ID</key>")){
                markpos=readituneslibrary.getFilePointer();
            }
            else if(line.contains("<key>Location</key>")){
                //this value will tell us if this header is our song
                String path=StringEscapeUtils.unescapeXml(line).replaceAll("%20", " ");//replace itunes escape chars
                if(path.contains(song)){
                    songfound=true;
                    readituneslibrary.seek(markpos);//reset back at the header and exit loop
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
     * Read the itunes library file and generate m3u playlists for each of the iTunes playlists.
     * The text should be send to the phone and the phone will write them to a m3u file.
     * @param readituneslibrary
     * @param musicDirectoryPath
     * @return 
     * @throws IOException 
     */
    public static ArrayList<Pair<String, ArrayList<String>>> generateM3UPlaylists(RandomAccessFile readituneslibrary) throws IOException{
        ArrayList<Pair<String,ArrayList<String>>> Array_of_List_Of_Playlists = new ArrayList<Pair<String,ArrayList<String>>>();
        readituneslibrary.seek(0);
        String line="";
        //wait until we get to the playlist section
        while(!line.contains("<key>Playlists</key>")){
            line=readituneslibrary.readLine();
        }
        
        //now that we are in the playlist section, we can search by <key>Name</key>, which is the same identifier as used in songs, so we had to wait until after the song section
        //iTunes contains by default the playlists "Library" and "Music", so ignore those
        while(line!=null){//read till end of file
            //reread if this line isnt the header of a new playlist or it is a header but it is the playlist Music or Library 
            while(line!=null && (!line.contains("<key>Name</key><string>") || line.contains("<key>Name</key><string>Music</string>") || line.contains("<key>Name</key><string>Library</string>"))){
                line=readituneslibrary.readLine();
            }
            
            if(line!=null){
                //if we've reached a unique playlist header
                //read its title and store it in the string to be paired with the arraylist
                String name=line.substring(line.indexOf("<string>")+8, line.indexOf("</string>"));
                ArrayList<String> playlistTracks = new ArrayList<String>();
                
                //read all the track ids and put them in the corresponding array list
                while(!line.contains("</array>")){
                    line=readituneslibrary.readLine();
                    if(line.contains("<key>Track ID</key><integer>")){
                        playlistTracks.add(line.substring(line.indexOf("<integer>")+9, line.indexOf("</integer>")));
                    }
                }
                
                //at the end of this playlist, put the playlist name and array of tracks in the master list
                Array_of_List_Of_Playlists.add(Pair.with(name, playlistTracks));
            }
        }
        
        //now that we have a list of all the playlists, we have to convert the track ids that we read into filepaths (remember to remove the super directory but keep file structure)
        readituneslibrary.seek(0);
        line="";//since we reach the end of the file in the last block, change this from null
        while(line!=null && !line.contains("<key>Playlists</key>")){//make sure we dont overshoot to reading playlists
            line=readituneslibrary.readLine();
            /*search through the arraylist for an id we find instead of searching the file for an id we want because
             * a) scanning through array is faster than reading file
             * b) the id can appear multiple times in the arrays (shared song between playlists) but only once in the file (unique id per song)
             */
            if(line.contains("<key>Track ID</key><integer>")){
                String track_id=line.substring(line.indexOf("<integer>")+9, line.indexOf("</integer>"));
                //read ahead till the location key
                while(!line.contains("<key>Location</key><string>")){
                    line=readituneslibrary.readLine();
                }
                //get location in a format that ignores the parent directory, i.e will match directory on phone
                String location=line.substring(line.indexOf("<string>")+8, line.indexOf("</string>"));
                location=StringEscapeUtils.unescapeXml(location).replaceAll("%20", " ");
                location=location.substring(location.indexOf("/iTunes Media/Music/")+20);
                
                //go through all the arrays for the track id
                for(Pair<String,ArrayList<String>> playlist:Array_of_List_Of_Playlists){
                    ArrayList<String> playlist_array_of_Ids=playlist.getValue1();
                    for(int i=0;i<playlist_array_of_Ids.size();i++){
                        //replace the track id in the array with the location if the found id matches
                        if(playlist_array_of_Ids.get(i).equals(track_id)){
                            playlist_array_of_Ids.set(i, location);
                        }
                    }
                }
            }
        }
        
        //return the list of playlists to be sent to the phone
        return Array_of_List_Of_Playlists;
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
