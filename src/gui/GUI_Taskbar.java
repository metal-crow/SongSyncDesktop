package gui;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import main.Desktop_Server;

public class GUI_Taskbar implements GUI_Parent{
    private static final PopupMenu popup = new PopupMenu();
    private static TrayIcon trayIcon;
    private static SystemTray tray;
    private static JFrame displayMenu =new JFrame("Sync Status");
    
    private static JLabel status_1=new JLabel();
    private static JLabel status_2=new JLabel();
    private static JProgressBar current_prog_bar=new JProgressBar();
    private int cur_prog=0;
    private static JTextArea status_progr=new JTextArea();

    public GUI_Taskbar() throws AWTException{
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
        displayMenu.setIconImage(new ImageIcon("icon.png").getImage());
        displayMenu.setPreferredSize(new Dimension(300,180));
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        displayMenu.setLocation(dim.width/2, dim.height/2);
        displayMenu.pack();
        displayMenu.setVisible(false);
        
        JPanel status=new JPanel();
        status.setLayout(new BoxLayout(status, BoxLayout.PAGE_AXIS));
        status.setPreferredSize(new Dimension(300,300));
        
        status_1.setAlignmentX(Component.CENTER_ALIGNMENT);
        status.add(status_1);
        
        status_2.setAlignmentX(Component.CENTER_ALIGNMENT);
        status.add(status_2);
        
        current_prog_bar.setVisible(false);
        status.add(current_prog_bar);

        status_progr.setAlignmentX(Component.CENTER_ALIGNMENT);
        status_progr.setEditable(false);
        status.add(status_progr);
        
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
    
    public void current_status(String a, int line){
        if(line==1){
            status_1.setText(a);
        }else if(line==2){
            status_2.setText(a);
            status_progr.append(a+"\n");
        }
    }
    
    public void progress_text(String a){
        status_progr.append(a);
    }
    
    public void progress_max(int a){
        current_prog_bar.setIndeterminate(false);
        current_prog_bar.setMaximum(a);
        current_prog_bar.setVisible(true);
        cur_prog=0;
    }
    
    public void progress_update(){
        current_prog_bar.setValue(cur_prog++);
    }
    
    public void exit(){
        tray.remove(trayIcon);
        displayMenu.dispose();
        Desktop_Server.exit();
    }

    @Override
    public void progress_indeterm() {
        current_prog_bar.setIndeterminate(true);
        current_prog_bar.setVisible(true);        
    }

    @Override
    public void reset() {
        //wait a few seconds before resetting
        current_prog_bar.setValue(0);
        status_2.setText("");
    }
}
