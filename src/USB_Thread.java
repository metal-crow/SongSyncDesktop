import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;


public class USB_Thread extends Thread {

    private String adbExe;
    private boolean connection;
    private String musicDirectoryPath;
    private String convertMusicTo;
    private boolean useiTunesDataLibraryFile;
    private RandomAccessFile readituneslibrary;
    private String iTunesDataLibraryFile;
    
    public USB_Thread(String adbExe, String musicDirectoryPath, String convertMusicTo, boolean useiTunesDataLibraryFile, RandomAccessFile readituneslibrary, String iTunesDataLibraryFile) {
        this.adbExe=adbExe;
        this.musicDirectoryPath = musicDirectoryPath;
        this.convertMusicTo = convertMusicTo;
        this.useiTunesDataLibraryFile = useiTunesDataLibraryFile;
        this.readituneslibrary = readituneslibrary;
        this.iTunesDataLibraryFile = iTunesDataLibraryFile;
        //start the adb listening daemon
        if(!adbExe.isEmpty()){
            Runtime runtime = Runtime.getRuntime();
            try {
                Process p=runtime.exec(adbExe+" start-server");
                p.waitFor();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                System.err.println("Error starting usb listener. Is your adb.exe location correct?");
            }
        }
    }
    
    /**
     * Whenever it detects a phone plugged in, auto sync. Do not sync anymore while phone plugged in unless user triggers.
     */
    @Override
    public void run() {
        //loop and listen for connection
        while(Desktop_Server.listen){
            //check to see if a phone is plugged in
            boolean newConnection=false;
            try{
                //execute command to get list of devices
                Runtime runtime = Runtime.getRuntime();
                Process p=runtime.exec(adbExe+" devices");
                BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream()));
                br.readLine();//ignore first line
                //read if a device connected
                if(!br.readLine().isEmpty()){
                    newConnection=true;
                }
                //if a device is not connected, reset connection state
                else{
                    connection=false;
                }
            }catch(IOException e){
                e.printStackTrace();
            }
            
            if(newConnection && !connection){
                connection=true;//phone is connected, dont resync
                System.out.println("Sync Connection via USB");
                
                //generate the list of all songs
                ArrayList<String> songs=new ArrayList<String>();
                Desktop_Server.generateList(songs, musicDirectoryPath);

                
            }
        }
        System.out.println("USB listener exiting.");
    }

    public void stop_connection() {
        Runtime runtime = Runtime.getRuntime();
        try {
            runtime.exec(adbExe+" kill-server");
        } catch (IOException e) {
            System.err.println("unable to kill usb listener.");
            e.printStackTrace();
        }
    }
}