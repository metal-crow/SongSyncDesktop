SongSync, The free and open source TuneSync replacement
==========================

Additional functions over TuneSync
-
This should work with any music list or program, just give the directory of all the music  
Option to convert the music files to another format when they are sent (FLAC to mp3)  
Ability to pass info about what music to delete from phone to computer  
Also offer wired transfer that works just like wifi transfer but done when phone plugged in  

Plan
-
Server listens for a connection from the phone  
Have app on android phone, when press sync button make p2p connection to server on computer  
When the phone connects, the server constructs a list of all music files in directory  
Server sends this list to the phone, phone saves it  
The phone gets the diff from its old list and finds what to remove and what it needs  
Phone sends list of needed songs to server  
Server reads this list and sends the music,which are saved to the phone's music directory  
  
Notes
-
ITunes doesnt write song info to music file metadata, cant assume that about the music for itunes or other media players.  
Just copy over the entire filestructure directly, so we dont worry about metadata or duplicates. That should have been handled by the music player when making the filestucture.    
   
**Using:**   
Apache common lang library  
JavaTuples  
  
**Dependencies:**  
ffmpeg  

FAQ
-
To use the usb connection ability:  
	1. The android adb exe must be linked in the ini. A version is included in the packaged zip file, but if you already have it installed, you need the adb.exe executable usually installed in C:\Users\[User]\AppData\Local\Android\android-sdk\platform-tools by the android sdk.  
	2. Make sure you have the drivers for your phone installed (use manufacuror's drivers), or the phone may not be detected when plugged in via usb.    
	3. Make sure your phone's Android debugging mode is turned on (under developer settings).
	
The app is using internal storage:
	Yeah. You have to root your phone, then enable all apps to write to the sd card. Its insecure, but I don't want to rewrite this for new android.
	
Untested, but the wifi sync feature should work over the internet. Just set the ip address, and set up port forwarding on the router.