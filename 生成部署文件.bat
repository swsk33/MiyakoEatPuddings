@echo off
set out=%systemdrive%\Users\%username%\Downloads\miyako.7z
7z a -t7z -mx=9 -x"!Resources\webres\css\*.scss" -xr"!Resources\webres\js\source" "%out%" ".\target\*.jar" "Resources"
echo �Ѿ����ɲ����ļ��������%out%���ڷ�������ѹ����������jar�ļ����ɡ�
echo ��������˳�...
pause > nul