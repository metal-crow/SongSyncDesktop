Additional functions over TuneSync

this should work with any music list or program, just give the directory of all the music
option to convert the music files to another format when they are sent (FLAC to mp3)
ability to pass info about what music to delete from phone to computer
also offer wired transfer that works just like wifi transfer but done when phone plugged in

Plan

Server listens for a connection from the phone
Have app on android phone, when press sync button make p2p connection to server on computer
When the phone connects, the server constructs a list of all music files in directory
Server sends this list to the phone, phone saves it
The phone gets the diff from its old list and finds what to remove and what it needs
Phone sends list of needed songs to server
Server reads this list and sends the music,which are saved to the phone's music directory

Notes
ITunes doesnt write song info to music file metadata, cant assume that about the music for itunes or other media players.
Just copy over the entire filestructure directly, so we dont worry about metadata or duplicates. That should have been handled by the music player when making the filestucture.

TODO
add itunes library info to mp3 tags before sending[ ]
choose what to convert song to[ ]
handle sending music over direct usb link[ ]
ability to pass info about what music to delete from phone to computer[ ]
send over playlists[ ]
Test what happens if song is dropped, fix it[ ]

Not using, but i found and i recommend the JAVE (Java Audio Video Encoder) library for converting audio.
