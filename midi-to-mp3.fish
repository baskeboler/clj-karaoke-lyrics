#!/usr/bin/env fish 

set progname "midi-to-mp3.fish"
set midifile $argv[1]
set outfile $argv[2]


function help
    echo "usage: $progname <midi file> <output mp3>"
end

function generate 
    timidity $midifile -Ow -o - | ffmpeg -i - -acodec libmp3lame -ab 64k $outfile    
end

generate 
or help
