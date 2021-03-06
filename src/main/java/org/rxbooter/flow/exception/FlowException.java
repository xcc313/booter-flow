package org.rxbooter.flow.exception;

/*
 * Copyright (c) 2017 Sergiy Yevtushenko
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
 *
 *
 */

/**
 * Base exception for flows.
 */
public class FlowException extends RuntimeException {
    private static final long serialVersionUID = -4685894495982223864L;

    public FlowException(String message) {
        super(message);
    }

    public FlowException(Throwable throwable) {
        super(throwable);
    }

    public FlowException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
