package main;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Scanner;

import threads.USB_Thread;
import threads.Wifi_Thread;


public class Desktop_Server {
    
    private static String musicDirectoryPath;
    private static String ffmpegEXElocation;
    private static String iTunesDataLibraryFile;
    private static String convertMusicTo;
    private static String ffmpegCommand;
    private static String adbExe;
    private static boolean listen=true;//thread's listen to know when to end
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
            e1.printStackTrace();
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
        Wifi_Thread wifi=new Wifi_Thread(musicDirectoryPath,convertMusicTo, useiTunesDataLibraryFile, readituneslibrary, iTunesDataLibraryFile, ffmpegEXElocation, ffmpegCommand, codecs);
        wifi.start();
        //start usb connection listener thread
        USB_Thread usb=new USB_Thread(adbExe,musicDirectoryPath,convertMusicTo, useiTunesDataLibraryFile, readituneslibrary, iTunesDataLibraryFile, ffmpegEXElocation, ffmpegCommand, codecs);
        if(!adbExe.isEmpty()){
            usb.start();
        }
        
        //listen for user command to end server
        String userend="";
        Scanner in=new Scanner(System.in);
        if(!adbExe.isEmpty()){
            System.out.println("Type 'c' to force a wired sync, 'end' to end the server.");
        }else{
            System.out.println("Type 'end' to end the server.");
        }
        while(listen){
            userend=in.next();
            if(userend.equalsIgnoreCase("end")){
                listen=false;
                wifi.stop_connection();
                usb.stop_connection();
            }else if(userend.equalsIgnoreCase("c") && !adbExe.isEmpty()){
                usb.try_force_connection();
            }
        }
        
        System.out.println("Exiting");
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
            Files.copy(inifile.toPath(), inifile_check.toPath(),StandardCopyOption.REPLACE_EXISTING);
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
                }else if(line.toLowerCase().contains("adblocation") && line.substring(12).length()>1){
                    adbExe=line.substring(12);
                }
            }
            line=initfileparams.readLine();
            tmp_line=chk_tmp_initfileparams.readLine();
        }
        initfileparams.close();
        chk_tmp_initfileparams.close();
        //make copy to verify against new changes now that we know what has changed for this session
        Files.copy(inifile.toPath(), inifile_check.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }

}
