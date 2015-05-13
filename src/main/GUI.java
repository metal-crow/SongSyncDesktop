package main;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class GUI {
    private static final PopupMenu popup = new PopupMenu();
    private static TrayIcon trayIcon;
    private static SystemTray tray;
    private static JFrame displayMenu =new JFrame("Sync Status");

    public GUI() throws AWTException{
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }
        
        //set logo
        trayIcon = new TrayIcon(new ImageIcon("icon_small.png").getImage());
        trayIcon.setImageAutoSize(true);
        
        //get tray
        tray = SystemTray.getSystemTray();
        
        //exit button
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exit();
                Desktop_Server.exit();
            }
        });
        popup.add(exit);
        
        //main menu
        displayMenu.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        displayMenu.setSize(100, 100);
        displayMenu.pack();
        displayMenu.setVisible(false);
        
        JPanel status=new JPanel();
        
        displayMenu.add(status);

        trayIcon.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e)){
                    displayMenu.setVisible(true);
                }
            }
        });

        trayIcon.setPopupMenu(popup);
        tray.add(trayIcon);
        
    }
    
    public void exit(){
        tray.remove(trayIcon);
        displayMenu.dispose();
    }
}
