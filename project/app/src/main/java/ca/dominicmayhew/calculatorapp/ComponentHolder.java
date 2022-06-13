package ca.dominicmayhew.calculatorapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import java.math.BigDecimal;

import ca.dominicmayhew.calculator.ComponentHandler;

class ComponentHolder {
    // Static properties.
    private static final int layoutResourceId = R.layout.component_container;
    private static ViewGroup root;
    protected static boolean nextBracePosIsBottom;

    // Layout components.
    protected LinearLayout containerLl;
    protected TextView contentTv;
    protected View errorBarV;

    protected ExpressionBuilder builder;
    private String tag;

    // Constructor.
    ComponentHolder(String content) {
        containerLl = inflateView(root);
        contentTv = containerLl.findViewById(R.id.contentTv);
        errorBarV = containerLl.findViewById(R.id.errorBarV);

        contentTv.setText(content);
        tag = content;
    }

    protected void attach(ExpressionBuilder builder) {
        this.builder = builder;
        builder.addToBack(this, this.getView());
    }

    protected void onDestroy(ExpressionBuilder builder) { return; }

    protected View getView() {
        return containerLl;
    }

    protected String getTag() { return tag; }

    protected void hideValueBrace() { return; }

    protected LinearLayout inflateView(ViewGroup root) {
        if (root == null) {
            throw new UnsupportedOperationException("Root must be set before component views can be created.");
        }
        LinearLayout ll = (LinearLayout) LayoutInflater.from(root.getContext())
                .inflate(layoutResourceId, root, false);
        ll.setId(View.generateViewId());
        return ll;
    }

    protected ComponentHandler handler = new ComponentHandler() {
        @Override
        public void notify(boolean isError, BigDecimal newValue) {
            errorBarV.setVisibility(isError ? View.VISIBLE : View.INVISIBLE);
        }
    };

    protected ComponentHandler getHandler() { return handler; };

    protected static void setRoot(ViewGroup root) {
        ComponentHolder.root = root;
    }

    public String toString() { return tag; }
}

class SubExpressionOpener extends ComponentHolder {
    private TextView valueTv;
    private ImageView braceIv;

    SubExpressionOpener(String tag) {
        super(tag);

        valueTv = containerLl.findViewById(R.id.topValueTv);
        braceIv = containerLl.findViewById(R.id.topBraceIv);

        handler = (boolean isError, BigDecimal newValue) -> {
            if (isError) {
                errorBarV.setVisibility(View.VISIBLE);
                valueTv.setVisibility(View.GONE);
                braceIv.setVisibility(View.GONE);
            } else {
                errorBarV.setVisibility(View.INVISIBLE);
                if (newValue != null) {
                    if (builder != null) {
                        builder.hideAllValueBraces();
                    }
                    valueTv.setVisibility(View.VISIBLE);
                    braceIv.setVisibility(View.VISIBLE);
                    valueTv.setText(ExpressionBuilder.getStringFromBd(newValue, ExpressionBuilder.precision)); // TODO: Should all be strings at this point?
                } else {
                    valueTv.setVisibility(View.GONE);
                    braceIv.setVisibility(View.GONE);
                }
            }
        };
    }

    @Override
    protected void attach(ExpressionBuilder builder) {
        super.attach(builder);
        builder.newForeground(containerLl.findViewById(R.id.foregroundLl));
    }

    @Override
    protected void onDestroy(ExpressionBuilder builder) {
        builder.closeLayout();
    }

    @Override
    protected LinearLayout getView() {
        return (LinearLayout) super.getView();
    }

    @Override
    protected void hideValueBrace() {
        valueTv.setVisibility(View.GONE);
        braceIv.setVisibility(View.GONE);
    }

    @Override
    protected LinearLayout inflateView(ViewGroup root) {
        if (root == null) {
            throw new UnsupportedOperationException("Root must be set before component views can be created.");
        }
        LinearLayout container = (LinearLayout) LayoutInflater.from(root.getContext())
                .inflate(R.layout.subexpression_container, root, false);
        return container;
    }

    public String toString() { return getTag(); }
}

class SubExpressionCloser extends ComponentHolder {
    LinearLayout parent;
    SubExpressionCloser(String tag) {
        super(tag);
    }

    @Override
    protected void attach(ExpressionBuilder builder) {
        super.attach(builder);
        parent = builder.getForeground(); // Must come before call to closeLayout().
        builder.closeLayout();
    }

    @Override
    protected void onDestroy(ExpressionBuilder builder) {
        builder.newForeground(parent);
    }

    public String toString() { return getTag(); }
}

