/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.axoniq.someproject.something;

import com.axoniq.someproject.SomeBean;
import com.axoniq.someproject.api.SingleChildCommand;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.interception.annotation.MessageHandlerInterceptor;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.annotation.TargetEntityId;

public record SingleAggregateChild(
        @TargetEntityId String id,
        String property
) {

    @MessageHandlerInterceptor
    public Object intercept(MessageHandlerInterceptorChain<?> chain, ProcessingContext processingContext) throws Exception {
        return chain.proceed(null, processingContext);
    }

    @CommandHandler
    public void handle(SingleChildCommand command, SomeBean someBean) {
        //left empty to not overcomplicate things
    }
}
