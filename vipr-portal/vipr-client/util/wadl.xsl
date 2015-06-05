<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:wadl="http://wadl.dev.java.net/2009/02">

<xsl:output method="text"/>

<xsl:template match="/">
    <xsl:apply-templates select="wadl:application/wadl:resources"/>
</xsl:template>

<xsl:template match="wadl:resources">
    <xsl:apply-templates select="wadl:resource"/>
</xsl:template>

<xsl:template match="wadl:resource">
    <xsl:variable name="path">
        <xsl:apply-templates select="." mode="path"/>
    </xsl:variable>
    <xsl:call-template name="indent"/>
    <xsl:value-of select="$path"/>
    <xsl:text>&#10;</xsl:text>
    <xsl:apply-templates select="wadl:method"/>
    <xsl:apply-templates select="wadl:resource"/>
</xsl:template>

<xsl:template match="wadl:resource" mode="path">
    <xsl:call-template name="resource-path">
        <xsl:with-param name="node" select="."/>
        <xsl:with-param name="path" select="''"/>
    </xsl:call-template>
</xsl:template>

<xsl:template match="wadl:method">
    <xsl:variable name="path">
        <xsl:apply-templates select="." mode="path"/>
    </xsl:variable>
    <xsl:call-template name="indent"/>
    <xsl:value-of select="@name"/>
    <xsl:text> </xsl:text>
    <xsl:value-of select="@id"/>
    <xsl:text>&#10;</xsl:text>
</xsl:template>

<xsl:template match="wadl:method" mode="path">
    <xsl:call-template name="resource-path">
        <xsl:with-param name="node" select="parent::wadl:resource"/>
        <xsl:with-param name="path" select="''"/>
    </xsl:call-template>
</xsl:template>

<xsl:template name="resource-path">
    <xsl:param name="node"/>
    <xsl:param name="path"/>
    
    <xsl:variable name="currentPath" select="concat($node/@path, $path)"/>
    <xsl:choose>
	    <xsl:when test="$node/parent::wadl:resource">
	        <xsl:call-template name="resource-path">
	            <xsl:with-param name="node" select="$node/parent::wadl:resource"/>
	            <xsl:with-param name="path" select="$currentPath"/>
	        </xsl:call-template>
	    </xsl:when>
	    <xsl:otherwise>
	       <xsl:value-of select="$currentPath"/>
	    </xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:template name="indent">
    <xsl:for-each select="ancestor::wadl:resource">
        <xsl:text>    </xsl:text>
    </xsl:for-each>
</xsl:template>

</xsl:stylesheet>