package scalaz
package data

sealed trait StateT[S, F[_], A] {
  val runT: S => F[(A, S)]

  import StateT._
  import WriterT._

  def *->* : (({type λ[α] = StateT[S, F, α]})#λ *->* A) =
    data.*->*.**->**[({type λ[α] = StateT[S, F, α]})#λ, A](this)

  def *->*->* : *->*->*[S, ({type λ[α, β] = StateT[α, F, β]})#λ, A] =
    data.*->*->*.**->**->**[S, ({type λ[α, β] = StateT[α, F, β]})#λ, A](this)

  def run(implicit i: F[(A, S)] =:= Ident[(A, S)]): S => (A, S) =
    runT(_).value

  def evalStateT(implicit f: Functor[F]): S => F[A] =
    s => f.fmap[(A, S), A](_._1)(runT(s))

  def evalState(implicit i: F[(A, S)] =:= Ident[(A, S)]): S => A =
    s => run(i)(s)._1

  def execStateT(implicit f: Functor[F]): S => F[S] =
    s => f.fmap[(A, S), S](_._2)(runT(s))

  def execState(implicit i: F[(A, S)] =:= Ident[(A, S)]): S => S =
    s => run(i)(s)._2

  def withStateT: (S => S) => StateT[S, F, A] =
    f => stateT[S, F, A](runT compose f)

  def withState(implicit i: F[(A, S)] =:= Ident[(A, S)]): (S => S) => State[S, A] =
    f => state[S, A](run(i) compose f)

  def writerT: S => WriterT[A, F, S] =
    s => WriterT.writerT(runT(s))

  def writer(implicit i: F[(A, S)] =:= Ident[(A, S)]): S => Writer[A, S] =
    s => WriterT.writer(run(i)(s))

  def map[B](f: A => B)(implicit ftr: Functor[F]): StateT[S, F, B] =
    stateT[S, F, B](s => ftr.fmap((as: (A, S)) => (f(as._1), as._2))(runT(s)))

  def flatMap[B](f: A => StateT[S, F, B])(implicit m: Bind[F]): StateT[S, F, B] =
    stateT[S, F, B](s => m.bind((as: (A, S)) => f(as._1) runT as._2)(runT(s)))
}

object StateT extends StateTs {

  def apply[S, F[_], A](r: S => F[(A, S)]): StateT[S, F, A] =
    stateT(r)
}

trait StateTs {
  type State[S, A] = StateT[S, Ident, A]

  type PartialApplyState[S] =
  PartialApply1Of2[State, S]

  def stateT[S, F[_], A](r: S => F[(A, S)]): StateT[S, F, A] = new StateT[S, F, A] {
    val runT = r
  }

  def state[S, A](r: S => (A, S)): State[S, A] =
    stateT[S, Ident, A](s => Ident.ident(r(s)))

  def getT[S, F[_]](implicit p: Pointed[F]): StateT[S, F, S] =
    stateT[S, F, S](s => p.point((s, s)))

  def get[S]: State[S, S] =
    state[S, S](s => (s, s))

  def putT[S, F[_]](s: => S)(implicit p: Pointed[F]): StateT[S, F, Unit] =
    stateT[S, F, Unit](_ => p.point(((), s)))

  def put[S](s: => S): State[S, Unit] =
    state[S, Unit](_ => ((), s))

  def modifyT[S, F[_]](f: S => S)(implicit mnd: Monad[F]): StateT[S, F, Unit] = {
    implicit val p = mnd.pointed
    implicit val b = mnd.bind
    getT[S, F] flatMap (s => putT[S, F](f(s)))
  }

  def modify[S](f: S => S): State[S, Unit] = {
    get[S] flatMap (s => put[S](f(s)))
  }

  implicit def StateTMonadTrans[S]: MonadTrans[({type λ[α[_], β] = StateT[S, α, β]})#λ] = new MonadTrans[({type λ[α[_], β] = StateT[S, α, β]})#λ] {
    def lift[G[_] : Monad, A](a: G[A]): StateT[S, G, A] =
      stateT(s => implicitly[Monad[G]].fmap((a: A) => (a, s))(a))
  }
}
