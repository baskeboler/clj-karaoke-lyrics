# clj-karaoke

[![Clojars Project](https://img.shields.io/clojars/v/baskeboler/clj-karaoke-lyrics.svg)](https://clojars.org/baskeboler/clj-karaoke-lyrics)

clojure functions and cli program that extract lyrics and timing data for sing-along display 

## Building

    $ lein uberjar 

## Usage


    $ java -jar clj-karaoke-lyrics.jar input-file.mid output.json --type json
    $ java -jar clj-karaoke-lyrics.jar -i <inputdir> -o <outputdir> -t ass --offset -1000
    
## Options

- `--type` either edn, ass or json 
- `--help` show help
- `-i` input directory with midi files 
- `-o` output directory where to store lyrics files 

## References 

- [Wiki format specification](https://github.com/colxi/midi-parser-js/wiki/MIDI-File-Format-Specifications)


## License

Copyright © 2021

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
