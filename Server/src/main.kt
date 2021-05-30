import java.util.*

fun main(){

    val s = Server()

    val db = DBHelper("matrix")
    db.createDatabase("matrix")
    db.fillTableFromCsv("data.csv")

    s.start("data",db)

    //Сервер остановится при вводе "STOP" в его консоль
    var cmd: String
    val sc= Scanner(System.`in`)
    do{
        cmd=sc.nextLine()
    }while (cmd!="STOP")
    s.stop()
}