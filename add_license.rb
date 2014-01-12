#!/usr/bin/ruby
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

A2_LICENSE = <<TEXT
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
TEXT
LICENSE_SNIPPET = " * Licensed under the Apache License, Version 2.0 (the \"License\");\n"
EXCLUDE_LIST = []

def process_file filename
  return if EXCLUDE_LIST.include? filename
  text = IO.readlines(filename)
  unless text.include? LICENSE_SNIPPET
    file = File.new(filename, "w")
    file.write A2_LICENSE
    file.write text
    puts "added license to #{filename}\n"
  end
end

Dir.glob("src/**/*.java").each { |filename|  process_file filename }
Dir.glob("test/**/*.java").each { |filename|  process_file filename }

