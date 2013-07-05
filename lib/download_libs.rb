#!/usr/bin/ruby
# This is minimalistic script to download plugin dependencies from maven central

def download(url, filename)
  if File.exists?(filename)
    puts "'#{filename}' is already downloaded... skipped"
  else
    `curl -O #{url}/#{filename}`
  end
end


download "http://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/2.0.6/", "groovy-all-2.0.6.jar"
download "http://repo1.maven.org/maven2/junit/junit/4.10/", "junit-4.10.jar"

download "http://repo1.maven.org/maven2/org/clojure/clojure/1.5.1/", "clojure-1.5.1.jar"
%w(scala-compiler scala-library scala-reflect).each { |scala_lib|
  download "http://repo1.maven.org/maven2/org/scala-lang/#{scala_lib}/2.10.2/", "#{scala_lib}-2.10.2.jar"
}

puts "Done"