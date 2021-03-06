package controllers

import javax.inject.Inject

import akka.actor.{ Props, ActorRef, Actor }
import play.api.Play.current
import play.api.mvc._
import play.api.i18n.Lang
import services.VocabularyService

class Quizz @Inject() (vocabulary: VocabularyService) extends Controller {

  def quizz(sourceLanguage: Lang, targetLanguage: Lang) = Action {
    vocabulary.findRandomVocabulary(sourceLanguage, targetLanguage).map { v =>
      Ok(v.word)
    } getOrElse {
      NotFound
    }
  }

  def check(sourceLanguage: Lang, word: String, targetLanguage: Lang, translation: String) = Action { request =>
    val isCorrect = vocabulary.verify(sourceLanguage, word, targetLanguage, translation)
    val correctScore: Int = request.session.get("correct").map(_.toInt).getOrElse(0)
    val wrongScore = request.session.get("wrong").map(_.toInt).getOrElse(0)
    if (isCorrect) {
      Ok.withSession("correct" -> (correctScore + 1).toString, "wrong" -> wrongScore.toString)
    } else {
      NotAcceptable.withSession("correct" -> correctScore.toString, "wrong" -> (wrongScore + 1).toString)
    }
  }

  def quizzEndpoint(sourceLang: Lang, targetLang: Lang) = WebSocket.acceptWithActor[String, String] { request =>
    out =>
      QuizzActor.props(sourceLang, targetLang, out, vocabulary)
  }

}

class QuizzActor(out: ActorRef, sourceLang: Lang, targetLang: Lang, vocabulary: VocabularyService) extends Actor {

  private var word = ""

  override def preStart(): Unit = sendWord()

  def receive = {
    case translation: String if vocabulary.verify(sourceLang, word, targetLang, translation) =>
      out ! "Correct"
      sendWord()
    case _ => out ! "Incorrect, try again!"
  }

  def sendWord() = {
    vocabulary.findRandomVocabulary(sourceLang, targetLang).map { v =>
      out ! s"Please translate '${v.word}'"
      word = v.word
    } getOrElse {
      out ! s"I don't know any word for ${sourceLang.code} and ${targetLang.code}"
    }
  }

}

object QuizzActor {

  def props(sourceLang: Lang, targetLang: Lang, out: ActorRef, vocabulary: VocabularyService): Props =
    Props(classOf[QuizzActor], out, sourceLang, targetLang, vocabulary)
}