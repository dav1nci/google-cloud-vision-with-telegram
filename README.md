# google-cloud-vision-with-telegram
###Using Google Cloud Vision for recognition of images and send result to telegram
####Need to be installed Maven 3 on your device to build this application
```
sudo apt-get install maven
```
Add Environment variable like this
```
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/yourCredentialJson.json
```
Build and run application like this
```
mvn clean package
java -jar target/label-1.0-SNAPSHOT.jar ~/path/to/yourImage.jpg
```
profit
