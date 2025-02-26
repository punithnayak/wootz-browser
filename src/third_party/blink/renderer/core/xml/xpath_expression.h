/*
 * Copyright 2005 Frerich Raabe <raabe@kde.org>
 * Copyright (C) 2006 Apple Computer, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef THIRD_PARTY_BLINK_RENDERER_CORE_XML_XPATH_EXPRESSION_H_
#define THIRD_PARTY_BLINK_RENDERER_CORE_XML_XPATH_EXPRESSION_H_

#include "third_party/blink/renderer/platform/bindings/script_wrappable.h"
#include "third_party/blink/renderer/platform/heap/member.h"

namespace WTF {
class String;
}  // namespace WTF

namespace blink {

class ExceptionState;
class ExecutionContext;
class Node;
class ScriptValue;
class V8XPathNSResolver;
class XPathResult;

namespace xpath {
class Expression;
}

class XPathExpression : public ScriptWrappable {
  DEFINE_WRAPPERTYPEINFO();

 public:
  static XPathExpression* CreateExpression(const WTF::String& expression,
                                           V8XPathNSResolver*,
                                           ExecutionContext* execution_context,
                                           ExceptionState&);

  XPathExpression();

  XPathResult* evaluate(ExecutionContext* execution_context,
                        Node* context_node,
                        uint16_t type,
                        const ScriptValue&,
                        ExceptionState&);

  void Trace(Visitor*) const override;

 private:
  Member<xpath::Expression> top_expression_;
};

}  // namespace blink

#endif  // THIRD_PARTY_BLINK_RENDERER_CORE_XML_XPATH_EXPRESSION_H_
