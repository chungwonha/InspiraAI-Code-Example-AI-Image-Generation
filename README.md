Install ty-dlp
  https://sourceforge.net/projects/yt-dlp.mirror/
Install ffmpeg
  
Set PATH to include the home directory where ty-dlp and ffmpeg are installed

java -DAWS_ACCESS_KEY_ID=<YOUR_ACCESS_KEY> -DAWS_REGION=<YOUR_REGION> -DAWS_SECRET_ACCESS_KEY=<YOUR_SECRET_ACCES_KEY> -DOPEN_AI_API_KEY=<YOUR_OPENAI_KEY> -DS3_BUCKET_NAME=<YOUR_S3_BUCKET> -DYTDL_HOME=<YOUR_YTDL_HOME> -jar inspiraai-0.0.1-SNAPSHOT.jar
