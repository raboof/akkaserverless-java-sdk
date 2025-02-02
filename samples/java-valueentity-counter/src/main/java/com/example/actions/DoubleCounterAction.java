/* This code was generated by Akka Serverless tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */

package com.example.actions;

import com.akkaserverless.javasdk.ServiceCallRef;
import com.akkaserverless.javasdk.SideEffect;
import com.akkaserverless.javasdk.action.ActionCreationContext;
import com.example.CounterApi;
import com.google.protobuf.Empty;

// tag::controller-forward[]
// tag::controller-side-effect[]
/**
 * An action.
 */
public class DoubleCounterAction extends AbstractDoubleCounterAction {

  private final ServiceCallRef<CounterApi.IncreaseValue> increaseCallRef;

  public DoubleCounterAction(ActionCreationContext creationContext) {
    this.increaseCallRef =
        creationContext.serviceCallFactory() // <1>
            .lookup(
                "com.example.CounterService",
                "Increase",
                CounterApi.IncreaseValue.class);
  }

  /**
   * Handler for "Increase".
   */
  @Override
  public Effect<Empty> increase(CounterApi.IncreaseValue increaseValue) {
  // end::controller-side-effect[]
    int doubled = increaseValue.getValue() * 2;
    CounterApi.IncreaseValue increaseValueDoubled =
        increaseValue.toBuilder().setValue(doubled).build(); // <2>

    return effects()
            .forward(increaseCallRef.createCall(increaseValueDoubled)); // <3>
  }

  // end::controller-forward[]
  public Effect<Empty> increaseWithSideEffect(CounterApi.IncreaseValue increaseValue) {
  // tag::controller-side-effect[]
    int doubled = increaseValue.getValue() * 2;
    CounterApi.IncreaseValue increaseValueDoubled =
        increaseValue.toBuilder().setValue(doubled).build(); // <2>

    return effects()
            .reply(Empty.getDefaultInstance()) // <3>
            .addSideEffect( // <4>
                SideEffect.of(increaseCallRef.createCall(increaseValueDoubled)));
  }
  // tag::controller-forward[]

}
// end::controller-forward[]
// end::controller-side-effect[]