@echo off
set output="%systemdrive%\Users\%username%\Downloads\miyako.7z"
mvn clean package && "%systemdrive%\Program Files\7-Zip\7z.exe" a -t7z -mx=9 -xr"!Resources\avatars\users" -xr"!Resources\webres\js\source" -x"!Resources\webres\css\*.scss" %output% ".\target\*.jar" "Resources" && echo �Ѿ�ѹ�������ļ���%output%����ѹ�������������ú�Resource\config\config.properties�ļ���Ȼ������jar���ɡ� && pause