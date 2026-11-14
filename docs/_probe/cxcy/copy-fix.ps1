# Fix: raw/jingsai_list.html is ALREADY UTF-8 (PowerShell decoded gbk then we wrote UTF-8).
# The *_utf8 variant was double-encoded mojibake. Copy the correct one.
Copy-Item -Path 'O:\dev\enroute-android\docs\_probe\cxcy\raw\jingsai_list.html' -Destination 'O:\dev\enroute-android\app\src\test\resources\feed\jingsai_list.html' -Force
'FIXED' | Set-Content -Path 'O:\dev\enroute-android\docs\_probe\cxcy\copy-fix-done.txt' -Encoding UTF8