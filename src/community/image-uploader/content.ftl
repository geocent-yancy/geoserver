<table border=1 class="featureInfo">
  <caption class="featureInfo">${type.name} Layer</caption>
  <#list features as feature>
      <#list feature.attributes as attribute>
        <#if !attribute.isGeometry
          && !attribute.name.equals("default_graphic_size")
          && !attribute.name.equals("version")
          && !attribute.name.equals("default_color")
          && !attribute.name.equals("default_graphic")
          && !attribute.name.equals("image_url") >
          <tr>
            <th>${attribute.name?replace("_", " ")?capitalize}</th>
            <td>${attribute.value}</td>
          </tr>
        <#elseif attribute.name.equals("image_url") && attribute.value?? && (attribute.value?trim?length > 0)>

          <#list attribute.value?split(",") as image_url>

            <tr>
              <th>Image</th>
              <td>
                <a href="${image_url}" target="_blank">
                  <img src="${image_url}" style="max-width:350px; height:auto; max-height:400px;" />
                </a>
              </td>
            </tr>

          </#list>

        </#if>
      </#list>
    <tr><td colspan='2'>&nbsp;</td></tr>
  </#list>
</table>
<br />
