#include "nativeServerConfig.h"

#include <QJsonArray>

#include "core/utils/containerEnum.h"
#include "core/utils/containers/containerUtils.h"
#include "core/utils/protocolEnum.h"
#include "core/utils/protocolEnum.h"
#include "core/protocols/protocolUtils.h"
#include "core/utils/constants/configKeys.h"
#include "core/utils/constants/protocolConstants.h"

namespace amnezia
{

using namespace ContainerEnumNS;

QJsonObject NativeServerConfig::XRaySubscriptionConfigs::toJson() const
{
    QJsonObject obj;

    if (!configString.isEmpty()) {
        obj[QLatin1String("config_string")] = configString;
    }

    if (!configName.isEmpty()) {
        obj[QLatin1String("config_name")] = configName;
    }

    return obj;
}

NativeServerConfig::XRaySubscriptionConfigs NativeServerConfig::XRaySubscriptionConfigs::fromJson(const QJsonObject &json)
{
    XRaySubscriptionConfigs xraySubscriptionConfigs;
    xraySubscriptionConfigs.configString = json.value(QLatin1String("config_string")).toArray();
    xraySubscriptionConfigs.configName = json.value(QLatin1String("config_name")).toArray();

    qDebug() << xraySubscriptionConfigs.configString.isEmpty();
    qDebug() << xraySubscriptionConfigs.configName.isEmpty();

    return xraySubscriptionConfigs;
}

bool NativeServerConfig::hasContainers() const
{
    return !containers.isEmpty();
}

ContainerConfig NativeServerConfig::containerConfig(DockerContainer container) const
{
    if (!containers.contains(container)) {
        return ContainerConfig{};
    }
    return containers.value(container);
}

QJsonObject NativeServerConfig::toJson() const
{
    QJsonObject obj;
    
    if (!description.isEmpty()) {
        obj[configKey::description] = this->description;
    }
    if (!hostName.isEmpty()) {
        obj[configKey::hostName] = hostName;
    }
    
    QJsonArray containersArray;
    for (auto it = containers.begin(); it != containers.end(); ++it) {
        QJsonObject containerObj = it.value().toJson();
        containersArray.append(containerObj);
    }
    if (!containersArray.isEmpty()) {
        obj[configKey::containers] = containersArray;
    }
    
    if (defaultContainer != DockerContainer::None) {
        obj[configKey::defaultContainer] = ContainerUtils::containerToString(defaultContainer);
    }
    
    if (!dns1.isEmpty()) {
        obj[configKey::dns1] = dns1;
    }
    if (!dns2.isEmpty()) {
        obj[configKey::dns2] = dns2;
    }

    QJsonObject xraySubscriptionConfigsObj = xraySubscriptionConfigs->toJson();
    if (!xraySubscriptionConfigsObj.isEmpty()) {
        obj[QLatin1String("xray_subscription_configs")] = xraySubscriptionConfigsObj;
    }

    if (currentConfig) {
        obj[QLatin1String("xray_subscription_config_current")] = currentConfig.value();
    }
    
    return obj;
}

NativeServerConfig NativeServerConfig::fromJson(const QJsonObject& json)
{
    NativeServerConfig config;
    
    config.description = json.value(configKey::description).toString();
    config.hostName = json.value(configKey::hostName).toString();
    
    QJsonArray containersArray = json.value(configKey::containers).toArray();
    for (const QJsonValue& val : containersArray) {
        QJsonObject containerObj = val.toObject();
        ContainerConfig containerConfig = ContainerConfig::fromJson(containerObj);
        
        QString containerStr = containerObj.value(configKey::container).toString();
        DockerContainer container = ContainerUtils::containerFromString(containerStr);
        
        config.containers.insert(container, containerConfig);
    }
    
    QString defaultContainerStr = json.value(configKey::defaultContainer).toString();
    config.defaultContainer = ContainerUtils::containerFromString(defaultContainerStr);
    
    config.dns1 = json.value(configKey::dns1).toString();
    config.dns2 = json.value(configKey::dns2).toString();

    config.xraySubscriptionConfigs->fromJson(json.value(QLatin1String("xray_subscription_config")).toObject());
    config.currentConfig = json.value(QLatin1String("xray_subscription_config_current")).toInt();

    qDebug() << config.xraySubscriptionConfigs.has_value();
    qDebug() << config.currentConfig.has_value();

    return config;
}

} // namespace amnezia

