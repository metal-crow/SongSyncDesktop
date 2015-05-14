package gui;

import java.util.Scanner;

import main.Desktop_Server;

public class GUI_cmd implements GUI_Parent{
    
    private static Scanner in=new Scanner(System.in);

    public GUI_cmd() {
        //listen for user command to end server
        String userend="";
        System.out.println("Type 'end' to end the server. Type 'R' to force a full resync, 'N' for a normal sync.");    
        while(true){
            userend=in.next();
            if(userend.equalsIgnoreCase("end")){
                break;
            }else if(userend.equalsIgnoreCase("R")){
                Desktop_Server.sync_type="R";
            }else if(userend.equalsIgnoreCase("N")){
                Desktop_Server.sync_type="N";
            }
        }
        in.close();
        exit();
    }

    @Override
    public void status_update(String a) {
        System.out.print(a);
    }

    @Override
    public void progress_update() {
        
    }

    @Override
    public void exit() {
        System.out.println("Exiting"); 
        Desktop_Server.exit();
    }

}
