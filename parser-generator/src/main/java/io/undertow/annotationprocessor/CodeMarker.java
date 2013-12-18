/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.annotationprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.jboss.classfilewriter.code.CodeLocation;
import org.jboss.classfilewriter.code.LookupSwitchBuilder;
import org.jboss.classfilewriter.code.TableSwitchBuilder;

/**
 * A code marker that doesn't care whether a branch is forward or backward.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class CodeMarker {
    private List<Object> branchEnds = new ArrayList<Object>();
    private CodeLocation location;

    public void mark(CodeAttribute c) {
        if (branchEnds == null) {
            throw new IllegalStateException("Marker already ended");
        }
        assert location == null;
        for (Object end : branchEnds) {
            if (end instanceof BranchEnd) {
                c.branchEnd((BranchEnd) end);
            } else if (end instanceof AtomicReference) {
                end = ((AtomicReference) end).get();
                if (end instanceof BranchEnd) {
                    c.branchEnd((BranchEnd) end);
                }
            }
        }
        branchEnds = null;
        location = c.mark();
    }

    public void gotoFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.gotoInstruction(location);
        } else {
            branchEnds.add(c.gotoInstruction());
        }
    }

    public void ifACmpEQFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifAcmpeq(location);
        } else {
            branchEnds.add(c.ifAcmpeq());
        }
    }

    public void ifACmpNEFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifAcmpne(location);
        } else {
            branchEnds.add(c.ifAcmpne());
        }
    }

    public void ifICmpEQFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifIcmpeq(location);
        } else {
            branchEnds.add(c.ifIcmpeq());
        }
    }

    public void ifICmpNEFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifIcmpne(location);
        } else {
            branchEnds.add(c.ifIcmpne());
        }
    }

    public void ifICmpLTFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifIcmplt(location);
        } else {
            branchEnds.add(c.ifIcmplt());
        }
    }

    public void ifICmpLEFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifIcmple(location);
        } else {
            branchEnds.add(c.ifIcmple());
        }
    }

    public void ifICmpGTFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifIcmpgt(location);
        } else {
            branchEnds.add(c.ifIcmpgt());
        }
    }

    public void ifICmpGEFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifIcmpge(location);
        } else {
            branchEnds.add(c.ifIcmpge());
        }
    }

    public void ifEQFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifEq(location);
        } else {
            branchEnds.add(c.ifeq());
        }
    }

    public void ifNEFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifne(location);
        } else {
            branchEnds.add(c.ifne());
        }
    }

    public void ifLTFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.iflt(location);
        } else {
            branchEnds.add(c.iflt());
        }
    }

    public void ifLEFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifle(location);
        } else {
            branchEnds.add(c.ifle());
        }
    }

    public void ifGTFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifgt(location);
        } else {
            branchEnds.add(c.ifgt());
        }
    }

    public void ifGEFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifge(location);
        } else {
            branchEnds.add(c.ifge());
        }
    }

    public void ifNullFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifnull(location);
        } else {
            branchEnds.add(c.ifnull());
        }
    }

    public void ifNotNullFrom(CodeAttribute c) {
        final CodeLocation location = this.location;
        if (location != null) {
            c.ifnotnull(location);
        } else {
            branchEnds.add(c.ifnotnull());
        }
    }

    public void switchFrom(LookupSwitchBuilder builder, int value) {
        final CodeLocation location = this.location;
        if (location != null) {
            builder.add(value, location);
        } else {
            branchEnds.add(builder.add(value));
        }
    }

    public void switchFrom(TableSwitchBuilder builder) {
        final CodeLocation location = this.location;
        if (location != null) {
            builder.add(location);
        } else {
            branchEnds.add(builder.add());
        }
    }
}
