# clj-karaoke

clojure functions and cli program that extract lyrics and timing data for sing-along display 
## Building

    $ lein uberjar 

## Usage


    $ java -jar clj-karaoke-0.1.0-standalone.jar input-file.mid output.json --type json

## Options

- `--type` either edn or json 
- `--help` show help
- `-i` input directory with midi files 
- `-o` output directory where to store lyrics files 

## License

Copyright © 2020 

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
