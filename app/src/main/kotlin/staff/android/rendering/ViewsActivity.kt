package staff.android.rendering

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import staff.android.rendering.catalog.*

class ViewsActivity : ComponentActivity() {
    private val scope=MainScope()
    private var saved=setOf<Long>()
    private var sort=SortOrder.Featured
    private lateinit var adapter: ProductAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saved=(savedInstanceState?.getLongArray("saved") ?: intent.getLongArrayExtra("saved") ?: longArrayOf()).toSet()
        sort=SortOrder.valueOf(savedInstanceState?.getString("sort") ?: "Featured")
        val snapshot=seedCatalog();val images=LocalImages(this);val builder=RowBuilder()
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(getColor(R.color.paper)) }
        root.setOnApplyWindowInsetsListener { v,insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            }
            insets
        }
        fun text(value:String,size:Float,bold:Boolean=false)=TextView(this).apply {
            text=value;textSize=size;setTextColor(getColor(R.color.ink));typeface=resources.getFont(if(bold) R.font.sora_bold else R.font.google_sans_regular)
            setPadding(dp(20),dp(8),dp(20),dp(8))
        }
        root.addView(text("Common Goods",24f,true))
        val heading=text("The everyday collection",24f,true);root.addView(heading)
        root.addView(text("Views comparison · same local catalog",14f))
        val sortButton=actionButton().apply {
            isAllCaps=false;text=if(sort==SortOrder.Featured) "Sort: Featured" else "Sort: Price"
            setOnClickListener {
                sort=if(sort==SortOrder.Featured) SortOrder.Price else SortOrder.Featured
                text=if(sort==SortOrder.Featured) "Sort: Featured" else "Sort: Price"
                adapter.submitList(prepareRows(builder,snapshot,sort))
            }
        }
        root.addView(sortButton,LinearLayout.LayoutParams(-1,-2).apply { setMargins(dp(20),dp(8),dp(20),dp(8)) })
        val list=RecyclerView(this).apply { layoutManager=LinearLayoutManager(this@ViewsActivity);setPadding(dp(20),dp(8),dp(20),dp(16));clipToPadding=false;contentDescription="Views collection" }
        adapter=ProductAdapter(images);list.adapter=adapter;adapter.submitList(prepareRows(builder,snapshot,sort))
        list.addOnScrollListener(object:RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView:RecyclerView,dx:Int,dy:Int) {
                heading.translationY=-recyclerView.computeVerticalScrollOffset().coerceAtMost(dp(18)).toFloat()
            }
        })
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);root.requestApplyInsets()
    }
    override fun onSaveInstanceState(outState:Bundle) { outState.putLongArray("saved",saved.toLongArray());outState.putString("sort",sort.name);super.onSaveInstanceState(outState) }
    override fun finish() { setResult(RESULT_OK,Intent().putExtra("saved",saved.toLongArray()));super.finish() }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
    private fun actionButton()=Button(this).apply {
        isAllCaps=false
        minimumHeight=dp(48)
        typeface=resources.getFont(R.font.google_sans_bold)
        setTextColor(getColor(R.color.accent))
        backgroundTintList=null
        val shape=GradientDrawable().apply {
            setColor(getColor(R.color.accent_soft));cornerRadius=dp(14).toFloat()
        }
        background=RippleDrawable(ColorStateList.valueOf(getColor(R.color.rule)),shape,null)
        setPadding(dp(16),dp(8),dp(16),dp(8))
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private inner class Holder(val card:LinearLayout,val image:ImageView,val name:TextView,val rating:TextView,val price:TextView,val save:Button):RecyclerView.ViewHolder(card) {
        var boundId=-1L
        var imageJob:Job?=null
    }
    private inner class ProductAdapter(private val images:LocalImages):ListAdapter<ProductRow,Holder>(ProductDiff) {
        init { setHasStableIds(true) }
        override fun getItemId(position:Int)=getItem(position).id
        override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):Holder {
            val large=resources.configuration.fontScale>=1.5f
            val card=LinearLayout(this@ViewsActivity).apply {
                orientation=if(large) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12),dp(12),dp(12),dp(12));background=GradientDrawable().apply { setColor(getColor(R.color.panel));cornerRadius=dp(22).toFloat() }
                layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=dp(14) }
            }
            val image=ImageView(this@ViewsActivity).apply { scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(getColor(R.color.panel)) }
            card.addView(image,LinearLayout.LayoutParams(if(large) -1 else dp(128),if(large) dp(180) else dp(128)))
            val details=LinearLayout(this@ViewsActivity).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,0,0) }
            card.addView(details,if(large) LinearLayout.LayoutParams(-1,-2) else LinearLayout.LayoutParams(0,-2,1f))
            fun label(size:Float,bold:Boolean=false)=TextView(this@ViewsActivity).apply { textSize=size;setTextColor(getColor(R.color.ink));typeface=resources.getFont(if(bold) R.font.google_sans_bold else R.font.google_sans_regular);setPadding(0,dp(4),0,dp(4)) }.also { details.addView(it) }
            val name=label(16f,true);val rating=label(12f);val price=label(16f,true)
            val save=actionButton();details.addView(save)
            return Holder(card,image,name,rating,price,save)
        }
        override fun onBindViewHolder(holder:Holder,position:Int) {
            val row=getItem(position);holder.boundId=row.id;holder.name.text=row.title;holder.rating.text=row.rating;holder.price.text=row.price
            holder.image.contentDescription=row.title;holder.image.setImageBitmap(images.cached(row.imageKey))
            holder.imageJob?.cancel();holder.imageJob=scope.launch { val bitmap=images.load(row.imageKey);if(holder.boundId==row.id) holder.image.setImageBitmap(bitmap) }
            holder.save.text=if(row.id in saved) "Saved" else "Save"
            holder.save.setOnClickListener { saved=toggleSaved(saved,row.id);holder.save.text=if(row.id in saved) "Saved" else "Save" }
        }
        override fun onViewRecycled(holder:Holder) { holder.boundId=-1;holder.imageJob?.cancel();holder.image.setImageDrawable(null);super.onViewRecycled(holder) }
    }
}

object ProductDiff:DiffUtil.ItemCallback<ProductRow>() {
    override fun areItemsTheSame(old:ProductRow,new:ProductRow)=old.id==new.id
    override fun areContentsTheSame(old:ProductRow,new:ProductRow)=old==new
}
