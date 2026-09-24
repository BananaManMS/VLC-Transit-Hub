circles = [
    (64, 40, 16),
    (112, 40, 20),
    (112, 90, 24),
    (160, 90, 17),
    (160, 138, 20),
    (208, 138, 24),
    (160, 186, 23),
    (112, 186, 20),
    (64, 232, 24),
    (112, 232, 17)
]

xml = ['<vector xmlns:android="http://schemas.android.com/apk/res/android"',
       '    android:width="96dp"',
       '    android:height="96dp"',
       '    android:viewportWidth="256"',
       '    android:viewportHeight="256">',
       '    <!-- Official Metrobús Valencia 10-Dot Chevron Logo -->']

for cx, cy, r in circles:
    path = f'    <path\n        android:fillColor="#F59E0B"\n        android:pathData="M{cx},{cy-r} a{r},{r} 0 1,0 0,{2*r} a{r},{r} 0 1,0 0,{-2*r} Z" />'
    xml.append(path)

xml.append('</vector>\n')

with open('app/src/main/res/drawable/logo_metrobus.xml', 'w') as f:
    f.write('\n'.join(xml))

print("Generated logo_metrobus.xml successfully")
