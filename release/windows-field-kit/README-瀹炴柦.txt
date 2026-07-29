MEIS Windows 鐜板満瀹炴柦鍖?
====================

鏈寘缁欍€愬疄鏂戒汉鍛樸€戠敤锛屼笉鏄畬鏁村紑鍙戜粨搴撱€?

鐩綍璇存槑
--------
  meis-*/target/*.jar   鍚庣鏈嶅姟
  www\                  鍓嶇闈欐€佽祫婧愶紙浜ょ粰 Nginx/IIS锛?
  scripts\              绮剧畝杩愮淮鑴氭湰锛堝惈瀹炴柦闈㈡澘锛?
  docs\                 閮ㄧ讲璇存槑

涓嶈鍋氱殑浜?
----------
  - 涓嶈鍦ㄥ鎴锋満璺?dev-panel.ps1锛?099锛屽紑鍙戜笓鐢級
  - 涓嶈鏈熸湜鏈寘鑳?mvn/npm 缂栬瘧锛堟簮鐮佹湭鍖呭惈锛?
  - 涓嶈鎶?5098 杩愮淮闈㈡澘瀵归櫌缃戝紑鏀?

鎺ㄨ崘姝ラ
--------
1. 瀹夎 JDK17 / PostgreSQL / Memurai(Redis) / MinIO / Nginx锛堣 docs锛?
2. 瑙ｅ帇鏈寘鍒颁緥濡?D:\meis\app
3. 閰嶇疆鐜鍙橀噺 POSTGRES_* / MINIO_* 绛?
4. 鍚姩杩愮淮闈㈡澘锛堜粎鏈満锛夛細

   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\ops-panel.ps1 -Token 鐜板満鍙ｄ护

   娴忚鍣ㄦ墦寮€ http://localhost:5098/
   鍏堛€屽惎鍔ㄦ牳蹇冦€嶆垨鎸夋枃妗ｉ『搴忓惎鍔紙tenant 蹇呴』鍏堣捣锛?

5. 楠岃瘉鐧诲綍鍚庯紝鎸?docs 鎶?JAR 娉ㄥ唽鎴?NSSM Windows 鏈嶅姟锛堥暱鏈熻繍琛岋級

鍛戒护琛屽閫夛紙鏃犻潰鏉匡級
--------------------
  powershell -File scripts\start.ps1
  powershell -File scripts\status.ps1
  powershell -File scripts\stop.ps1

鍏抽棴闈㈡澘涓嶄細鍋滄宸插惎鍔ㄧ殑涓氬姟鏈嶅姟銆?
