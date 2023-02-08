# MetaDSL
Parser and interpreter to create slick mobile applications without being a developer.
It uses onsen UI but can be modified to use other libraries.
Idea is allow a specialized developer to only modify the parser for the external DSL and non developers with basic understanding of the DSL can create applications.

In the folder examples is a ready made example with a fully compiled parser and supporting files. You can open the index.html inside a web browser and open it with ?dev to see the script and modify it.
The web broswer console can provide you with error messages and it can be debugged in the web browser too if using a debug version of the parser (i.e sbt fastLinkJS instead of fullLinkJS).

# Building
sbt fullLinkJS

Add index.html or index-dev.html and script.js file containing the script using the externally defined DSL to the directory local to the optimized JS.

Run by opening in a web browser.

Opening it with ?dev opens a console where a DSL script can be tried so the app also defines an interactive interpreter of sorts.

Awesome to use with Cordova and package as a mobile app.

Enjoy!
