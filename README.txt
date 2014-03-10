################################################################################
# Welcome to extendo-android, aka the Brainstem
#
# This is an Android app for connecting Extendo devices (Typeatron,
# Extend-o-Hand) with an Extend-o-Brain knowledge base and event handlers.
#
# For more details on the Extendo project, see:
#     https://github.com/joshsh/extendo     

# run extendo-android in the emulator
mvn clean install \
    && adb shell pm uninstall -k net.fortytwo.extendo \
    && adb install target/extendo-android-*.apk

# uninstall
adb uninstall net.fortytwo.extendo

# create Amarino Maven dependency
mvn install:install-file -DgroupId=at.abraxas -DartifactId=amarino -Dversion=2.0 -Dpackaging=jar -DcreateChecksum=true -Dfile=AmarinoLibrary.jar
