#!/usr/bin/env fish 

set input_dir $argv[1];

for f in $input_dir/*.{kar,mid}*.edn
  set newname (echo $f | replace ".kar" "" ".mid" "");
  if not string match $newname $f
    echo "$f needs to be renamed to $newname";
    mv $f $newname;
  end
end
