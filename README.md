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
  
`ToDo`:
 
   * <s>add itunes library info to mp3 tags before sending</s>  
   * <s>add itunes album art to mp3</s>  
   * <s>send over playlists</s>  
   * chose what playlists to transfer  
   * <s>choose what to convert song to</s>  
   * delete all songs on phone and resend them if conversion type changes  
   * handle sending music over direct usb link  
   * ability to pass info about what music to delete from phone to computer  
   * Test what happens if song is dropped, fix it  
   * Add a way for user to end the server  
  
**Using:**   
Apache common lang library  
JavaTuples  
  
**Dependencies:**  
ffmpeg  