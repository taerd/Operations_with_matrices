import java.io.File
import java.util.*
import kotlin.random.Random


fun main(){
    //GenerateThreeFloatMatrix(25,25,25,25)


    val s = Server()

    val db = DBHelper("matrix")
    db.createDatabase("matrix")
    //db.fillTableFromCsv("data.csv")
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
public fun GenerateTwoFloatMatrix(n1:Int,k:Int,m2:Int){
    val writer = File("data/data.csv").bufferedWriter()
    for (i in 1..n1){
        for(j in 1..k){
            writer.write("1;"+i+";"+j+";"+Random.nextFloat()+"\n")
        }
    }
    for (i in 1..k){
        for(j in 1..m2){
            writer.write("2;"+i+";"+j+";"+Random.nextFloat()+"\n")
        }
    }
    //writer.write("2;"+k+";"+m2+";"+Random.nextFloat())
    writer.close()
}
public fun GenerateThreeFloatMatrix(n1:Int,k:Int,m2:Int,m3:Int){
    val writer = File("data/data.csv").bufferedWriter()
    for (i in 1..n1){
        for(j in 1..k){
            writer.write("1;"+i+";"+j+";"+Random.nextFloat()+"\n")
        }
    }
    for (i in 1..k){
        for(j in 1..m2){
            writer.write("2;"+i+";"+j+";"+Random.nextFloat()+"\n")
        }
    }
    for (i in 1..k){
        for(j in 1..m3){
            writer.write("3;"+i+";"+j+";"+Random.nextFloat()+"\n")
        }
    }
    //writer.write("2;"+k+";"+m2+";"+Random.nextFloat())
    writer.close()
}