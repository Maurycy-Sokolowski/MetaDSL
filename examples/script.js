var staticScript = `site "ScaleIt mW8" mobile dark with {
       add "Login" login show
           background "https://picsum.photos/1980/1020"
               with {
               add "Login" text
               add "Password" password
	       add "Remember me" remember
               add "Login" button with {
                   transition "ScaleIt mW8 main" with {
                       query post "https://api.scaleitusa.com/api/auth" inputs {
                           "username" using "Login"
                           "password" using "Password"
                       }
                   }
               }
           }
       add "Scales"
			title "Scales"
			message "This summarizes the scales"
			table display {
				query "https://api.scaleitusa.com/api/" use {
					rest "companyID"
					rest "scales"
               		param "locationId="
					param "locationID"
					param "session="
					param "sessionKey"
				} with {
               		column "name" as "Name"
					column "weight" as "Weight"
					column "unit" as "Unit"
					column "scaleId" as "ScaleId"
				}
			}
			panel "Weight" {
				query "https://api.scaleitusa.com/api/" use {
						rest "companyID"
						rest "scales"
						param "locationId="
						param "locationID"
						param "session="
						param "sessionKey"
				} with {
						column "name" as "Name"
						column "weight" as "Weight"
						column "unit" as "Unit"
						value "weight" as "S2Weight"
						value "lastContact" as "S2DateTime"
				   }
			   }
			panel "Truck" {
				query "https://api.scaleitusa.com/api/scaleit/v1/vehicles/" use {
						rest "locationID"
						param "session="
						param "sessionKey"
				} with {
						column "id" as "Truck ID"
						column "name" as "Truck Name"
						column "regNo" as "RegNo"
						value "regNo" as "vehicleRegNo"
						value "vehicleWeights(0).tareWeight" as "S1Weight"
						value "vehicleWeights(0).tareDate" format "YYYY-MM-DD HH:mm:ss" as "S1DateTime"
			   }
		   }
			panel "Client" {
				query "https://api.scaleitusa.com/api/scaleit/v1/clients/" use {
						rest "locationID"
						param "session="
						param "sessionKey"
				} with {
						column "id" as "Account ID"
						column  "name" as "Client Name"
						value "name" as "clients"
						value "id" as "clientId"
				}
		   }
			panel "Project" {
			query "https://api.scaleitusa.com/api/scaleit/v1/projects/" use {
						rest "locationID"
						param "session="
						param "sessionKey"
				} with {
						column "id" as "Project ID"
						column  "name" as "Project Name"
						column "client_name" as "Client"
						value "id" as "projectId"
				}
		   }
			panel "Material" {
				query "https://api.scaleitusa.com/api/scaleit/v1/products/" use {
					rest "locationID"
					param "session="
					param "sessionKey"
				} with {
					column "id" as "Product ID"
					column "name" as "Product Name"
					column "unitPrice" as "Unit Price" format "$ %5.2f"
					value "id" as "productId"
				}
		   }
		   panel "Signature" signature "Provide signature" as "signature"
		   panel "Camera" camera "Provide camera" as "orderLineMedia"
			go "Tickets" with {
				query post body "https://api.scaleitusa.com/api/scaleit/v1/tickets/" use {
						   rest "locationID"
						   rest "mobile"
						   param "session="
							param "sessionKey"
				   } with {
						
				   }
			}
       add "Tickets"
           title "Tickets"
           message "This summarizes the tickets"
           table display {
               query "https://api.scaleitusa.com/api/scaleit/v1/tickets/" use {
                   rest "locationID"
				   param "session="
					param "sessionKey"
               } with {
                   column "ticketNumber" as "Ticket #"
                   column "clientName" as "Client"
                   column "ticketDetails(0).netWeight" as "Weight"
                   column "ticketDetails(0).weightUnit" as "Unit"
               }
           }
	    panel "Ticket" {
           	"netWeight" is "Weigh"
           }
	add "About"
           title "About"
           lines {
		"This is an application to do self weighing"
		"Created by Berrye LLC for ScaleIt USA Inc."
		"Copyright © 2020."
	   }
	    page
       add "ScaleIt mW8 main" main color "blue" with {
           add "username" display "Edit User" with {
               query put "https://api.scaleitusa.com/api/users" inputs {
                   "Username" using "username" hidden
                   "Old Password" using "oldpassword" password
                   "New Password" using "newpassword" password
               }
           }
           add "Company,companies,companyID,companyName,locations,locationID,locationName" menu with {
               transition "Scales"
           }
           add "ScaleIt mW8 0.8.1" footer
           add "Scales" load
       }
   }`;