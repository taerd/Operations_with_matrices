import java.io.File
import java.sql.*

class DBHelper (
    val dbName : String,
    val address : String = "localhost",
    val port : Int = 3306,
    val user : String = "root",
    val password : String = "root"
){
    private var connection : Connection?=null
    private var statement: Statement? = null

    /**
     * Метод создания базы данных с указанием файла, где есть sql-запросы
     * @param userdata - имя файла с запросами бд
     */
    fun createDatabase(userdata : String){
        connect()
        dropAllTables()
        createTablesFromDump(userdata)
    }

    /**
     * Метод подключения к бд  $dbName
     * Обращение к субд через statement (sql запросы)
     */
    private fun connect(){
        statement?.run{
            if (!isClosed) close()
        }
        var rep = 0
        do {
            try {
                connection =
                    DriverManager.getConnection("jdbc:mysql://$address:$port/$dbName?serverTimezone=UTC",
                        user,
                        password
                    )
                statement =
                    DriverManager.getConnection("jdbc:mysql://$address:$port/$dbName?serverTimezone=UTC",
                        user,
                        password
                    ).createStatement()
            } catch (e: SQLSyntaxErrorException) {
                println("Ошибка подключения к бд ${dbName} : \n${e.toString()}")
                println("Попытка создания бд ${dbName}")
                val tstmt =
                    DriverManager.getConnection("jdbc:mysql://$address:$port/?serverTimezone=UTC", user, password)
                        .createStatement()
                tstmt.execute("CREATE SCHEMA `$dbName`")
                tstmt.closeOnCompletion()
                rep++
            }
        } while (statement == null && rep < 2)
    }

    /**
     * Метод для удаления таблиц,если они есть в бд
     */
    private fun dropAllTables(){
        println("Удаление всех таблиц в базе данных...")
        statement?.execute("DROP TABLE if exists `data`")
        println("Все таблицы удалены.")
    }

    /**
     * Создание таблиц через готовые sql запросы в файле
     * @param userdata - название файла
     */
    private fun createTablesFromDump(userdata : String){
        println("Создание структуры базы данных из дампа...")
        val validData = userdata.split(".sql")
        try {
            var query = ""
            File(validData[0]+".sql").forEachLine {
                if(!it.startsWith("--") && it.isNotEmpty()){
                    query += it
                    if (it.endsWith(';')) {
                        statement?.addBatch(query)
                        query = ""
                    }
                }
            }
            statement?.executeBatch()
            println("Структура базы данных успешно создана.")
        }
        catch (e: SQLException){
            println(e.message)
        }
        catch (e: Exception){
            println(e.message)
        }
    }

    /**
     * Метод закрытия подключения.утверждения
     */
    fun disconnect() {
        statement?.close()
    }

    /**
     * Метод заполнения таблицы через insert into, в каждое поле кортежа записываются данные
     * в формате "$name", где name - значение типа string, СУБД преобразует varchar в нужные форматы полей
     * @param userdata - название таблицы
     */
    fun fillTableFromCsv(userdata : String){
        val validData = userdata.split(".csv")
        println("Заполнение данными таблицы ${validData[0]}...")
        try{
            val bufferedData = File("data/"+validData[0]+".csv").bufferedReader()
            val requestTemplate = "INSERT INTO `${validData[0]}`" +
                    "("+ getDataFromTable(validData[0]) +") VALUES "
            while(bufferedData.ready()){
                var request = "$requestTemplate("
                val data = bufferedData.readLine().split(';')
                data.forEachIndexed { i, name ->
                    request += "\"$name\""
                    if(i<data.size -1) request+=','
                }
                request+=')'
                statement?.addBatch(request)
            }
            statement?.executeBatch()
            statement?.clearBatch()
            println("Таблица ${validData[0]} успешно заполнена!")
        } catch(e: Exception){
            println(e.toString())
        }

    }

    /**
     * Метод выдает данные из таблиц в субд (реализованно название атрибутов таблицы, закоментирован код для доставания типов атрибутов таблицы)
     * @param userdata название таблицы
     */
    private fun getDataFromTable(userdata : String): String{
        val query = "SHOW COLUMNS FROM `${userdata}`"
        val resultSet = statement?.executeQuery(query)
        var resultData = ""
        if (resultSet != null){
            //get column name
            while (resultSet.next()){
                resultData+= "`"+resultSet.getString(1)+"` ,"
            }
            resultData= resultData.substring(0,(resultData.length)-2)
            /*
            if(columnName){
                //get column name
                while (resultSet.next()){
                    resultData+= "`"+resultSet.getString(1)+"` ,"
                }
                resultData= resultData.substring(0,(resultData.length)-4)
            }
            else {
                //get type of column ( Not Worked now )
                while (resultSet.next()){
                    val currentField = resultSet.getString(1)
                    val validField = currentField.split("(")
                    println(validField)
                    println(map.get(validField[0]))
                    resultData+= map.get(validField[0])
                }
            }
             */
        }
        return resultData
    }

    /**
     * Выдает данные из таблицы
     * @param table - таблица из которой нужно достать данные
     * @param value - значение которое нужно получить
     * @param field - по какому полю проводить поиск
     * @param key - какие совпадения искать
     * @return List<Float> со значениями
     */
    fun getDataFromTable(table : String,value : String,field : String,key : Int) : List<Float>{
        val query = "SELECT ${value} FROM ${table} WHERE ${field}=${key}"
        val rs =statement?.executeQuery(query)
        val resultList = mutableListOf<Float>()
        while(rs?.next()==true){
            resultList.add(rs.getString(1).toFloat())
        }
        return resultList
    }

    /**
     * Функция для выполнения команд в бд
     * (плохая реализация, нет проверки на инъекции и тд)
     * @param query - строка которую нужно выполнить
     */
    public fun ExecuteQueryWithoutCheck(query:String){
        statement?.executeUpdate(query)
    }
}