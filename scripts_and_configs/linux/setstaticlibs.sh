#!/bin/sh
cd build_libs/lib/
ln -s /usr/lib/libfreetype.a
ln -s /usr/lib/libfontconfig.a
ln -s /usr/lib/libfribidi.a
ln -s /usr/lib/libz.a
ln -s /usr/lib/libexpat.a

cd pkgconfig
ln -s /usr/lib/pkgconfig/freetype2.pc
ln -s /usr/lib/pkgconfig/fontconfig.pc
ln -s /usr/lib/pkgconfig/fribidi.pc

cd - 
