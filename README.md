# Casa & Co. Home Decor Store

## MySQL setup

1. Install MySQL and the MySQL Connector/J JAR.
2. Run `schema.sql` in MySQL Workbench or the MySQL CLI.
3. Create a `lib` folder inside this project and put the Connector/J JAR inside it.
4. Set these PowerShell variables, using your own password:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/home_decor_store?useSSL=false&serverTimezone=UTC"
$env:DB_USER = "root"
$env:DB_PASSWORD = "your-mysql-password"
```

## Run

The easiest option is to use the included launcher. It prompts for your MySQL password without saving it to a file:

```powershell
.\run.ps1
```

If PowerShell blocks scripts, run this once for the current terminal:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

Then run `.\run.ps1` again.

Manual option:

```powershell
javac -cp ".;lib/*" HomeDecorStore.java DBConnection.java
java -cp ".;lib/*" HomeDecorStore
```

Open http://localhost:8080 and select the account icon. New visitors can create an account; passwords are stored as PBKDF2 hashes with a unique salt.
