package gui;

public interface GUI_Parent {    
    
    public void exit();
    
    public void current_status(String a, int line);
    
    public void progress_max(int a);
    
    public void progress_update();
    
    public void progress_text(String a);
}
