1. Auf beiden Rechnern Github Desktop installieren
2. Auf dem Steuerungs iMac Java installieren
3. auf dem selben iMac Visual Studio Code und alle vorgeschlagenen Java-Plugins installieren
4. das Github-Repo auf beide iMacs clonen (https://github.com/KlarinSteffens/WFTstreamOverlay)
5. In Visual Studio Code das Repo als Ordner öffnen
6. In OBS den Websocket-Server aktivieren und die Verbindunsinformationen in app.java im src-Subfolder bei den Variablen serverUri und password hinzufügen (ggf. alte Einträge auskommentieren)
7. Scene Collection aus dem Repo in OBS importieren (ggf. Grafiken updaten. Nicht vergessen die gleiche Dateibezeichnung zu nutzen, zu speichern und ins Repo zu pushen)
8. Replay Buffer aktiveren und starten sowie gewünschte Länge einstellen (bisher immer 10s)
9. Recording Path überprüfen und im Zweifelsfall in Java updaten (Rec Settings .MKV Dateien, Use Stream Encoder für Video und FFmpeg AAC für Audio (Kann zwischen mac OS und Windows variieren(Regie iMac von letzem Mal finden und ggf readme.txt updaten)))
10. Sichergehen dass die Torsong audioquelle einen Output hat aber nicht im Stream auftaucht!!!!!
11. TESTEN!!!! (zum starten in vsc F5 drücken und eine valide matches.json auswählen, wenn Programm nicht startet dann war es eine invalide .json (braucht 4 Komponenten(away, id, title, home)))

12. mit Youtube verbinden und TESTEN!!
13. Matches.json Dateien updaten
14. Torsongs einspeisen (ACHTUNG Torsongs müssen exakt genauso heißen wie Teams im Matches.json, ansonsten funktioniert es nicht!)

PS: Es kann auch sein dass ihr das MoveTransition und weiter OBS Plugins braucht!
